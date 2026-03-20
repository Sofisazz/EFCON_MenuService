package com.example.menuservice.service.V2.serviceImpl;

import com.example.menuservice.dto.*;
import com.example.menuservice.dto.mapping.EatingPlanMapper;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.Recipe;
import com.example.menuservice.entity.RecipeIngredient;
import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Measure;
import com.example.menuservice.enums.Status;
import com.example.menuservice.exceptions.ExistsException;
import com.example.menuservice.exceptions.MissingException;
import com.example.menuservice.feignclient.InventoryClient;
import com.example.menuservice.feignclient.UserClient;
import com.example.menuservice.map.AllergenMap;
import com.example.menuservice.repository.EatingPlanRepository;
import com.example.menuservice.repository.RecipeRepository;
import com.example.menuservice.service.V2.EatingPlanServiceV2;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class EatingPlanServiceImplV2 implements EatingPlanServiceV2 {

    private final EatingPlanRepository eatingPlanRepository;
    private final RecipeRepository recipeRepository;

    private final EatingPlanMapper eatingPlanMapper;
    private final RecipeMapper recipeMapper;

    private final AllergenMap allergenMap;

    private final UserClient userClient;
    private final InventoryClient inventoryClient;

    @Override
    public Page<EatingPlanDto> findAllEatingPlansForUser(PageRequest pageable, int userId) {
        if ( !userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        Page<EatingPlan> eatingPlans = eatingPlanRepository.findByUserId(userId, pageable);

        return eatingPlans.map(eatingPlanMapper::toDto);
    }

    @Override
    public EatingPlanDto findEatingPlanByIdForUser(int id, int userId) {
        if ( !userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingPlan eatingPlan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("Плана питания с id'" + id +"' для пользователя с userId '" + userId +"' не существует"));

        return eatingPlanMapper.toDto(eatingPlan);
    }


    @Override
    public boolean existEatingPlanByIdForUser(int id, int userId) {
        if ( !userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        return eatingPlanRepository.existsByIdAndUserId(id, userId);
    }

    @Transactional
    @Override
    public EatingPlanDto createEatingPlanForUser(int userId, EatingPlanDto eatingPlanDto) {
        if ( !userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingType type = eatingPlanDto.getType();
        LocalDate date = eatingPlanDto.getDate();

        if (!type.equals(EatingType.SNACK)) {
            if (eatingPlanRepository.existsByDateAndTypeAndUserIdAndStatusNot(date, type, userId, Status.CANCELLED)) {
                throw new ExistsException("План питания на " + date + ": " + type + " уже существует и не отменен. Сначала отмените его или создайте новый");
            }
        }
        else {
            long activeSnacksCount = eatingPlanRepository.countByDateAndTypeAndUserIdAndStatusNot(date, type, userId, Status.CANCELLED);

            if (activeSnacksCount >= 5) {
                throw new ExistsException("Превышен лимит перекусов (5 шт.) на " + date);
            }
        }

        List<RecipeDto> recipes = generateRecipes(userId);

        boolean isRecipeSafe = recipes.stream().anyMatch(recipe -> recipe.getId() == eatingPlanDto.getRecipeId());


        Integer recipeId = eatingPlanDto.getRecipeId();
        Recipe receivedRecipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + recipeId + "' не найден"));

        if (!isRecipeSafe) {
            UserRestrictionsDto user = userClient.getUserRestrictions(userId);
            String error = validateRecipe(receivedRecipe, user);

            if (!error.equals("OK")) {
                throw new MissingException(error);
            }
        }

        checkAvailability(userId, receivedRecipe);

        EatingPlan receivedEatingPlan = eatingPlanMapper.toEntity(eatingPlanDto);
        receivedEatingPlan.setStatus(Status.CREATED);
        receivedEatingPlan.setUserId(userId);
        receivedEatingPlan.setRecipe(receivedRecipe);
        subtractIngredientsFromInventory(receivedEatingPlan, userId);

        eatingPlanRepository.save(receivedEatingPlan);
        return eatingPlanMapper.toDto(receivedEatingPlan);
    }


    @Transactional
    @Override
    public EatingPlanDto updateEatingPlan(int id, int userId, EatingPlanDto eatingPlanDto) {
        if ( !userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingPlan existingPlan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("План питания с id '" + id + "' не найден у пользователя c id '" + userId + "'"));

        if (!eatingPlanDto.getType().equals(EatingType.SNACK) && eatingPlanRepository.existsByDateAndTypeAndUserIdAndIdNot(eatingPlanDto.getDate(), eatingPlanDto.getType(),userId, id)) {
            throw new ExistsException("План питания на " + eatingPlanDto.getDate() + ": " + eatingPlanDto.getType() + " существует для пользователя с id '" + userId +"'");
        }

        Status currentStatus = existingPlan.getStatus();
        if (currentStatus.equals(Status.IN_PROGRESS) || currentStatus.equals(Status.CONSUMED)) {
            throw new ExistsException("Нельзя изменить план, который уже готовится или был съеден. Отмените его или создайте новый.");
        }

        boolean wasCancelled = currentStatus.equals(Status.CANCELLED);
        Recipe newRecipe;
        Integer recipeId = eatingPlanDto.getRecipeId();

        if (recipeId != null && recipeId != existingPlan.getRecipe().getId()) {

            newRecipe = recipeRepository.findByIdAndOwnerIdIsNullOrOwnerId(recipeId, userId)
                    .orElseThrow(() -> new MissingException("Рецепт с id '" + eatingPlanDto.getRecipeId() + "' не найден"));

            UserRestrictionsDto user = userClient.getUserRestrictions(userId);
            String error = validateRecipe(newRecipe, user);
            if (!error.equals("OK")) {
                throw new MissingException(error);
            }

            checkAvailability(userId, newRecipe);
        } else {
            newRecipe = existingPlan.getRecipe();
        }

        eatingPlanMapper.updateFromDto(eatingPlanDto, existingPlan);
        existingPlan.setUserId(userId);
        existingPlan.setRecipe(newRecipe);

        if (wasCancelled) {
            existingPlan.setStatus(Status.CREATED);
        }

        eatingPlanRepository.save(existingPlan);

        return eatingPlanMapper.toDto(existingPlan);
    }

    @Transactional
    @Override
    public void deleteEatingPlan(int id, int userId) {
        if (!userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingPlan plan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("План питания с id '" + id + "' не найден у пользователя с id '" + userId +"'"));
        if (plan.getStatus() != Status.CONSUMED) {
            returnIngredientsToInventory(plan, userId);
        }

        eatingPlanRepository.deleteById(id);
    }

    @Transactional
    @Override
    public EatingPlanDto updateStatusEatingPlan(int id, int userId, Status status) {
        if (!userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingPlan receivedEatingPlan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() ->  new MissingException("План питания с id '" + id + "' не найден у пользователя с id '" + userId +"'"));

        Status oldStatus = receivedEatingPlan.getStatus();

        if (status.equals(Status.CANCELLED) && oldStatus.equals(Status.CONSUMED)) {
            throw new MissingException("План питания на " + oldStatus + ": " + receivedEatingPlan.getDate() + " уже съеден. Отменить нельзя");
        }

        if (!status.equals(Status.CANCELLED)) {
            boolean isSlotOccupied = eatingPlanRepository.existsByDateAndTypeAndUserIdAndIdNotAndStatusNot(receivedEatingPlan.getDate(), receivedEatingPlan.getType(), userId, id, Status.CANCELLED);

            if (isSlotOccupied) {
                throw new ExistsException("Невозможно активировать план: на " + receivedEatingPlan.getType() + ": " + receivedEatingPlan.getDate() + ". Уже существует другой активный план на это время");
            }
        }

        receivedEatingPlan.setStatus(status);

        if (status.equals(Status.CANCELLED)) {
            returnIngredientsToInventory(receivedEatingPlan, userId);
        }

        eatingPlanRepository.save(receivedEatingPlan);

        return  eatingPlanMapper.toDto(receivedEatingPlan);
    }

    @Override
    public ShoppingListDto getShoppingListForPlan(int planId, int userId) {
        if (!userClient.checkUserExists(userId)) {
            throw new MissingException("Пользователь с id '" + userId + "' не существует");
        }

        EatingPlan plan = eatingPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new MissingException("План питания с id '" + planId + "' не существует"));

        Recipe recipe = plan.getRecipe();
        List<RecipeIngredient> ingredients = recipe.getIngredients();
        int numberOfPeople = plan.getNumberOfPeople();
        int recipeServings = recipe.getServing();

        if (ingredients == null || ingredients.isEmpty()) {
            return createShoppingList(recipe, List.of());
        }

        List<String> ingredientsNames = ingredients.stream().map(RecipeIngredient::getName).toList();
        List<ProductStatusDto> allStatuses;

        try {
            allStatuses = inventoryClient.generateShoppingList(userId, ingredientsNames);
        } catch (FeignException e) {
            throw new MissingException("Сервис склада недоступен");
        }

        List<ProductStatusDto> needToBuy = new ArrayList<>();
        for (RecipeIngredient ingredient : ingredients) {
            ProductStatusDto productStatusDto = null;

            for (ProductStatusDto status : allStatuses) {
                if (status.getName().equalsIgnoreCase(ingredient.getName())) {
                    productStatusDto = status;
                }
            }

            Measure unit;
            if (productStatusDto != null && productStatusDto.getUnit() != null) {
                unit = productStatusDto.getUnit();
            } else {
                unit = ingredient.getUnit();
            }

            double hasAmount;
            if (productStatusDto != null) {
                hasAmount = productStatusDto.getAvailableAmount();
            } else {
                hasAmount = 0.0;
            }

            double quantityInRecipe = ingredient.getQuantity();
            double requiredAmount = (quantityInRecipe / recipeServings) * numberOfPeople;

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

    @Override
    public boolean existEatingPlanWithRecipe(int id, int recipeId, int userId) {
        EatingPlan plan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("План c id '" + id +"' не найден для пользователя с id '" + userId + "'"));

        Recipe planRecipe = plan.getRecipe();
        return planRecipe.getId() == recipeId;
    }

    private void subtractIngredientsFromInventory(EatingPlan plan, int userId) {
        Recipe recipe = plan.getRecipe();
        int numberOfPeople = plan.getNumberOfPeople();
        int recipeServings = recipe.getServing();

        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            double realAmountUsed = (ingredient.getQuantity() / recipeServings) * numberOfPeople;

            ConsumeProductDto product = createConsumeProductDto(userId, ingredient, realAmountUsed);
            inventoryClient.consumeProduct(product);
        }
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


    private List<RecipeDto> generateRecipes(int userId){
        UserRestrictionsDto user = userClient.getUserRestrictions(userId);
        List<Recipe> existingRecipes = recipeRepository.findAll();
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

    private String validateRecipe(Recipe recipe, UserRestrictionsDto user) {
        List<String> ingredients = getIngredientNames(recipe);

        String allergyError = filterAllergy(ingredients, user.getAllergies());
        if (!allergyError.equals("OK")) {
            return "Нельзя добавить рецепт '" + recipe.getName() + "'. Причина: " + allergyError;
        }

        String foodError = filterFoods(ingredients, user.getUnfavoriteFoods());
        if (!foodError.equals("OK")) {
            return "Нельзя добавить рецепт '" + recipe.getName() + "'. Причина: " + foodError;
        }

        String triggerError = filterTriggers(ingredients, user.getFoodTriggers());
        if (!triggerError.equals("OK")) {
            return "Нельзя добавить рецепт '" + recipe.getName() + "'. Причина: " + triggerError;
        }

        return "OK";
    }


    private void checkAvailability(int userId, Recipe recipe) {
        List<String> ingredients = getIngredientNames(recipe);

        List<ProductAvailabilityDto> statusList = inventoryClient.checkAvailability(userId ,new ArrayList<>(ingredients));

        for (ProductAvailabilityDto item : statusList) {
            if (!item.isAvailable()) {
                throw new MissingException("Нельзя создать план: ингредиент '" + item.getProductName() + "' недоступен. Причина: " + item.getMessage());
            }
        }
    }


    private void returnIngredientsToInventory(EatingPlan plan, int userId) {
        Recipe recipe = plan.getRecipe();

        if (recipe == null || recipe.getIngredients() == null) {
            return;
        }

        int numberOfPeople = plan.getNumberOfPeople();
        int recipeServings = recipe.getServing();

        for (RecipeIngredient ingredient : recipe.getIngredients()) {

            double realAmountUsed = (ingredient.getQuantity() / recipeServings) * numberOfPeople;

            ConsumeProductDto dto = createConsumeProductDto(userId, ingredient, realAmountUsed);

            try {
                inventoryClient.returnProduct(dto);
            } catch (FeignException e) {
                log.info("Не удалось вернуть продукт '" + ingredient.getName() + "' при отмене плана. Причина: " + e.getMessage());
            }
        }
    }

    private ConsumeProductDto createConsumeProductDto(int userId, RecipeIngredient ingredient, double realAmountUsed) {
        ConsumeProductDto dto = new ConsumeProductDto();
        dto.setUserId(userId);
        dto.setProductName(ingredient.getName());
        dto.setAmount(realAmountUsed);
        dto.setUnit(ingredient.getUnit());

        return dto;
    }

}
