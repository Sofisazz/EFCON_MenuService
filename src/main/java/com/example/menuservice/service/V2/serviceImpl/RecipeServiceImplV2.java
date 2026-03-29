package com.example.menuservice.service.V2.serviceImpl;

import com.example.menuservice.comparator.RecipeExpiringComparator;
import com.example.menuservice.dto.*;
import com.example.menuservice.dto.mapping.RecipeIngredientMapper;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.PlanItem;
import com.example.menuservice.entity.Recipe;
import com.example.menuservice.entity.RecipeIngredient;
import com.example.menuservice.enums.Measure;
import com.example.menuservice.exceptions.ExistsException;
import com.example.menuservice.exceptions.MissingException;
import com.example.menuservice.exceptions.UpdateException;
import com.example.menuservice.feignclient.InventoryClient;
import com.example.menuservice.feignclient.UserClient;
import com.example.menuservice.map.AllergenMap;
import com.example.menuservice.repository.PlanItemRepository;
import com.example.menuservice.repository.RecipeRepository;
import com.example.menuservice.service.V2.RecipeServiceV2;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecipeServiceImplV2 implements RecipeServiceV2 {

    private final RecipeRepository recipeRepository;
    private final PlanItemRepository planItemRepository;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper recipeIngredientMapper;

    private final InventoryClient inventoryClient;
    private final UserClient userClient;

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final AllergenMap allergenMap;


    @Override
    public List<RecipeDto> getAllRecipes(int userId) {
        circuitBreakerUserExists(userId);

        List<RecipeDto> recipes;
        recipes = recipeRepository.findByOwnerIdOrOwnerIdIsNull(userId)
                .stream().map(recipeMapper::toDto).toList();

        return recipes;

    }

    @Override
    public RecipeDto getRecipeById(int id, int userId) {
        circuitBreakerUserExists(userId);

        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));


        if (recipe.getOwnerId() != null && !recipe.getOwnerId().equals(userId)) {
            throw new MissingException("Рецепт с id '" + id + "' не существует для пользователя с id '" + userId + "'");
        }
        return recipeMapper.toDto(recipe);
    }

    @Override
    public List<RecipeDto> generateRecipesAccordingRestrictions(PageRequest pageable, int userId) {
        circuitBreakerUserExists(userId);

        return generateRecipes(userId);
    }

    @Override
    public List<RecipeDto> getRecipesSortedByExpiringIngredients(int userId) {
        circuitBreakerUserExists(userId);

        List<TransferProductDto> expiringProducts = circuitBreakerGetExpiring(userId);

        Optional.ofNullable(expiringProducts)
                .orElseThrow(() -> new MissingException("Нет связи с inventory сервером"));

        if (expiringProducts.isEmpty()) {
            List<Recipe> recipes = recipeRepository.findByOwnerIdIsNullOrOwnerId(userId);
            return recipes.stream().map(recipeMapper::toDto).toList();
        }

        LocalDate today = LocalDate.now();

        Map<String, Integer> expiringScores = new HashMap<>();
        for (TransferProductDto product : expiringProducts) {
            if (product.getName() != null && !product.getName().isBlank()) {
                String nameLower = product.getName().toLowerCase();

                long daysUntil = 0;
                if (product.getExpirationDate() != null) {
                    daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, product.getExpirationDate());
                }

                int score;
                if (daysUntil <= 1) {
                    score = 10;
                } else if (daysUntil <= 3) {
                    score = 5;
                } else {
                    score = 2;
                }

                expiringScores.put(nameLower, score);
            }
        }

        List<Recipe> allRecipes = recipeRepository.findByOwnerIdIsNullOrOwnerId(userId);

        allRecipes.sort(new RecipeExpiringComparator(expiringScores));

        List<RecipeDto> recipeDtos;
        recipeDtos = allRecipes.stream().map(recipeMapper::toDto).toList();

        return  recipeDtos;
    }

    @Override
    public ShoppingListDto getShoppingListForRecipe(int recipeId, int userId) {
        circuitBreakerUserExists(userId);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + recipeId + "' не существует"));

        if (recipe.getOwnerId() != null && !recipe.getOwnerId().equals(userId)) {
            throw new MissingException("Рецепт с id '" + recipeId + "' не существует");
        }

        List<RecipeIngredient> ingredients = recipe.getIngredients();

        if (ingredients == null || ingredients.isEmpty()) {
            return createShoppingList(recipe, List.of());
        }
        List<String> ingredientsNames = ingredients.stream().map(RecipeIngredient::getName).toList();
        List<ProductStatusDto> allStatuses = circuitBreakerGenerateShoppingList(ingredientsNames, userId);

        List<ProductStatusDto> needToBuy = new ArrayList<>();
        for (RecipeIngredient ingredient : ingredients) {
            ProductStatusDto productStatusDto = null;
            for (ProductStatusDto status : allStatuses) {
                if (status.getName().equalsIgnoreCase(ingredient.getName())) {
                    productStatusDto = status;
                }
            }

            double hasAmount;
            if (productStatusDto != null) {
                hasAmount = productStatusDto.getAvailableAmount();
            } else {
                hasAmount = 0.0;
            }

            double requiredAmount = ingredient.getQuantity();

            Measure unit;
            if (productStatusDto != null && productStatusDto.getUnit() != null) {
                unit = productStatusDto.getUnit();
            } else {
                unit = ingredient.getUnit();
            }

            double toBuyAmount = Math.max(0, requiredAmount - hasAmount);
            if (toBuyAmount > 0) {
                if (productStatusDto == null) {
                    productStatusDto = createProductStatusDto(ingredient, unit);
                }

                productStatusDto.setRequiredAmount(requiredAmount);
                productStatusDto.setToBuyAmount(toBuyAmount);

                needToBuy.add(productStatusDto);
            }
            }

        return createShoppingList(recipe, needToBuy);
    }

    @Transactional
    @Override
    public RecipeDto createRecipeV2(RecipeDto recipeDto, Integer userId) {
        circuitBreakerUserExists(userId);

        String name = recipeDto.getName();

        if (userId == null) {
            if (recipeRepository.existsByNameAndOwnerIdIsNull(name)) {
                throw new ExistsException("Рецепт с названием '" + name + "' уже существует в глобальной базе");
            }

            if (recipeRepository.existsByNameAndOwnerIdIsNotNull(name)) {
                throw new ExistsException(
                        "Невозможно создать глобальный рецепт '" + name + "', так как это название уже используется одним из пользователей в личных рецептах."
                );
            }

        } else {
            if (recipeRepository.existsByNameAndOwnerIdIsNull(name)) {
                throw new ExistsException("Нельзя использовать название '" + name + "', так как оно зарезервировано для глобального рецепта");
            }

            if (recipeRepository.existsByNameAndOwnerId(name, userId)) {
                throw new ExistsException("У вас уже есть личный рецепт с названием '" + name + "'");
            }
        }

        Recipe recipe = recipeMapper.toEntity(recipeDto);
        recipe.setOwnerId(userId);

        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                ingredient.setRecipe(recipe);
            }
        }

        recipeRepository.save(recipe);

        return recipeMapper.toDto(recipe);
    }

    @Transactional
    @Override
    public void deleteRecipeByIdV2(int id, Integer userId) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));

        if (userId == null) {
            if (recipe.getOwnerId() != null) {
                throw new ExistsException("Разработчик может удалять только продукты из глобальной базы");
            }

            notifyAboutConnectedPlans(recipe);
            recipeRepository.deleteById(id);

            return;
        }

        circuitBreakerUserExists(userId);

        if (recipe.getOwnerId() == null) {
            throw new ExistsException("Невозможно удалить рецепт из глобальной базы. Вы можете удалить только свои личные рецепт");
        }

        if (!recipe.getOwnerId().equals(userId)) {
            throw new ExistsException("Вы не можете удалить этот рецепт, так как он принадлежит другому пользователю");
        }

        notifyAboutConnectedPlans(recipe);

        recipeRepository.deleteById(id);
    }

    @Transactional
    @Override
    public RecipeDto updateRecipeV2(int id, RecipeDto recipeDto, Integer userId) {
        Recipe existingRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));

        if (userId == null) {
            if (existingRecipe.getOwnerId() != null) {
                throw new ExistsException("Разработчик может изменять только глобальные рецепты");
            }
        } else {
            circuitBreakerUserExists(userId);

            if (existingRecipe.getOwnerId() == null) {
                throw new ExistsException("Пользователи не могут изменять глобальные рецепты.");
            }
            if (!existingRecipe.getOwnerId().equals(userId)) {
                throw new ExistsException("Вы не можете изменить этот рецепт, так как он принадлежит другому пользователю.");
            }
        }

        existsRecipeByName(recipeDto.getName(), id, userId);
        recipeMapper.updateFromDto(recipeDto, existingRecipe);

        if (recipeDto.getIngredients() != null) {
            existingRecipe.getIngredients().clear();

            for (RecipeIngredientDto ingredientDto : recipeDto.getIngredients()) {
                RecipeIngredient ingredient = recipeIngredientMapper.toEntity(ingredientDto);

                ingredient.setRecipe(existingRecipe);
                existingRecipe.getIngredients().add(ingredient);
            }
        } else {
            existingRecipe.getIngredients().clear();
        }

        recipeRepository.save(existingRecipe);
        recipeRepository.flush();

        return recipeMapper.toDto(existingRecipe);
    }

    private ProductStatusDto createProductStatusDto(RecipeIngredient ingredient, Measure unit) {
        ProductStatusDto product = new ProductStatusDto();
        product.setName(ingredient.getName());
        product.setUnit(unit);
        product.setAvailable(false);
        product.setAvailableAmount(0.0);

        return product;
    }

    private ShoppingListDto createShoppingList(Recipe recipe, List<ProductStatusDto> needToBuy) {
        ShoppingListDto list = new ShoppingListDto();
        list.setRecipeId(recipe.getId());
        list.setRecipeName(recipe.getName());
        list.setNeedToBuy(needToBuy);

        return list;
    }

    private void existsRecipeByName(String name, int id, Integer userId) {
        if (userId == null) {
            if (recipeRepository.existsByNameAndOwnerIdIsNull(name)) {

                Recipe receivedRecipe = recipeRepository.findByNameAndOwnerIdIsNull(name);

                if (receivedRecipe != null && receivedRecipe.getId() != id) {
                    throw new UpdateException("Рецепт с названием '" + name + "' уже существует в глобальной базе");
                }
            }
        } else {
            if (recipeRepository.existsByNameAndOwnerIdIsNull(name)) {
                throw new UpdateException("Нельзя использовать название '" + name + "', так как оно зарезервировано для рецепта в глобальной базе");
            }

            if (recipeRepository.existsByNameAndOwnerId(name, userId)) {
                Recipe receivedRecipe = recipeRepository.findByNameAndOwnerId(name, userId);
                if (receivedRecipe != null && receivedRecipe.getId() != id) {
                    throw new UpdateException("У вас уже есть личный рецепт с названием '" + name + "'");
                }
            }
        }
    }

    private List<RecipeDto> generateRecipes(int userId){
        UserRestrictionsDto user = circuitBreakerGetUserRestrictions(userId);

        List<Recipe> existingRecipes = recipeRepository.findByOwnerIdOrOwnerIdIsNull(userId);
        List<Recipe> filteredRecipes = filterRecipes(existingRecipes, user);
        List<RecipeDto> newRecipes;
        newRecipes = filteredRecipes.stream().map(recipeMapper::toDto).toList();

        return newRecipes ;
    }

    private List<Recipe> filterRecipes(List<Recipe> recipes, UserRestrictionsDto user) {
        List<String> allergiesFromUser = user.getAllergies();
        List<String> unfavoriteFoods = user.getUnfavoriteFoods();
        List<String> triggers = user.getFoodTriggers();
        List<Recipe> goodRecipes = new ArrayList<>();


        for (Recipe recipe : recipes) {
            List<String> ingredients = getIngredientNames(recipe);
            String isAllergySafe = filterAllergy(ingredients, allergiesFromUser);
            String isFoodSafe = filterFoods(ingredients, unfavoriteFoods);
            String isTriggerSafe = filterTriggers(ingredients, triggers);

            if (isAllergySafe.equals("OK") && isFoodSafe.equals("OK") && isTriggerSafe.equals("OK")) {
                goodRecipes.add(recipe);
            }
        }

        return goodRecipes;
    }

    private List<String> getIngredientNames(Recipe recipe) {
        List<String> names = new ArrayList<>();

        if (recipe.getIngredients() == null) {
            return names;
        }

        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            names.add(ingredient.getName());
        }

        return names;
    }

    private String filterAllergy(List<String> ingredients, List<String> allergiesFromUser) {
        List<String> allergies = Optional.ofNullable(allergiesFromUser)
                .orElse(List.of());

        for (String allergy : allergies) {
            for (String ingredient : ingredients) {
                if (allergenMap.containsAllergen(ingredient, allergy)) {
                    return "У вас есть аллергия на '" + allergy + "', а в рецепте найден ингредиент: '" + ingredient + "'";
                }
            }
        }

        return "OK";
    }

    private String filterFoods(List<String> ingredients, List<String> unfavoriteFoodsFromUser) {
        List<String> unfavoriteFoods = Optional.ofNullable(unfavoriteFoodsFromUser)
                .orElse(List.of());

        for (String food : unfavoriteFoods) {
            for (String ingredient : ingredients) {
                if (ingredient.toLowerCase().contains(food.toLowerCase())) {
                    return "Вы указали нелюбимый продукт '" + food + "', который содержится в ингредиенте: '" + ingredient + "'";
                }
            }
        }

        return "OK";
    }

    private String filterTriggers(List<String> ingredients, List<String> triggersFromUser) {
        List<String> triggers = Optional.ofNullable(triggersFromUser)
                .orElse(List.of());

        for (String trigger : triggers) {
            for (String ingredient : ingredients) {
                if (ingredient.toLowerCase().contains(trigger.toLowerCase())) {
                    return "У вас есть пищевой триггер '" + trigger + "', который найден в ингредиенте: '" + ingredient + "'";
                }
            }
        }

        return "OK";
    }

    private void notifyAboutConnectedPlans(Recipe recipe) {
        List<PlanItem> planItems = planItemRepository.findByRecipeId(recipe.getId());

        if (planItems.isEmpty()) {
            return;
        }

        List<String> connectedPlansInfo = new ArrayList<>();

        for (PlanItem item : planItems) {
            EatingPlan plan = item.getEatingPlan();
            connectedPlansInfo.add("План id '" + plan.getId() + ": " + plan.getType() + " от " +plan.getDate());
        }

        throw new ExistsException("Невозможно удалить рецепт '" + recipe.getName() + "', так как он включен в следующие планы питания: \n" + String.join(", ", connectedPlansInfo) + ". Сначала удалите рецепт из этих планов или отмените планы");
    }

    private void circuitBreakerUserExists(Integer userId){
        boolean exists = executeWithCircuitBreaker("userService", () -> userClient.checkUserExists(userId));

        if (!exists) {
            throw new MissingException("Пользователя с id '" + userId + "' не существует");
        }
    }

    private UserRestrictionsDto circuitBreakerGetUserRestrictions(int userId) {

        return executeWithCircuitBreaker("userService", () -> userClient.getUserRestrictions(userId));
    }

    private List<ProductStatusDto> circuitBreakerGenerateShoppingList(List<String> ingredientsNames, int userId) {

        return executeWithCircuitBreaker("inventoryService", () -> inventoryClient.generateShoppingList(userId, ingredientsNames));
    }

    private List<TransferProductDto> circuitBreakerGetExpiring(int userId) {

        return executeWithCircuitBreaker("inventoryService", () -> inventoryClient.getExpiring(userId));
    }

    private <T> T executeWithCircuitBreaker(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(cb, supplier);

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {

            log.warn("Circuit Breaker '{}' разомкнут. Сервис недоступен", circuitBreakerName);
            throw new MissingException(circuitBreakerName + " временно недоступен");
        } catch (MissingException e) {

            throw e;
        } catch (Exception e) {

            log.error("Ошибка при вызове сервиса через CB '{}': {}", circuitBreakerName, e.getMessage(), e);
            throw new MissingException("Ошибка связи с " + circuitBreakerName);
        }
    }
}
