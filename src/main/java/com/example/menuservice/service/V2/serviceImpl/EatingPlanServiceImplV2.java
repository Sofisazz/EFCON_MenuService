package com.example.menuservice.service.V2.serviceImpl;

import com.example.menuservice.dto.*;
import com.example.menuservice.dto.mapping.EatingPlanMapper;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.PlanItem;
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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

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

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Page<EatingPlanDto> findAllEatingPlansForUser(PageRequest pageable, int userId) {
        circuitBreakerUserExists(userId);

        Page<EatingPlan> eatingPlans = eatingPlanRepository.findByUserId(userId, pageable);

        return eatingPlans.map(eatingPlanMapper::toDto);
    }

    @Override
    public EatingPlanDto findEatingPlanByIdForUser(int id, int userId) {
        circuitBreakerUserExists(userId);

        EatingPlan eatingPlan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("Плана питания с id'" + id +"' для пользователя с userId '" + userId +"' не существует"));

        return eatingPlanMapper.toDto(eatingPlan);
    }


    @Override
    public boolean existEatingPlanByIdForUser(int id, int userId) {
        circuitBreakerUserExists(userId);

        return eatingPlanRepository.existsByIdAndUserId(id, userId);
    }

    @Transactional
    @Override
    public EatingPlanDto createEatingPlanForUser(int userId, EatingPlanDto eatingPlanDto) {
        circuitBreakerUserExists(userId);

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

        List<PlanItemDto> items = eatingPlanDto.getItems();

        if(items == null || items.isEmpty()) {
            throw  new MissingException("План питания должен содержать хотя бы одно блюдо");
        }

        UserRestrictionsDto userRestrictions = circuitBreakerGetUserRestrictions(userId);

        EatingPlan receivedEatingPlan = eatingPlanMapper.toEntity(eatingPlanDto);
        receivedEatingPlan.setStatus(Status.CREATED);
        receivedEatingPlan.setUserId(userId);

        Map<String, ProductRequirementDto> totalRequirements = new HashMap<>();
        List<PlanItem> tempPlanItems = new ArrayList<>();

        for (PlanItemDto itemDto : items) {
            Integer recipeId = itemDto.getRecipeId();
            PlanItem newItem;

            if (recipeId != null) {

                Recipe recipe = recipeRepository.findById(recipeId)
                        .orElseThrow(() -> new MissingException("Рецепт с id '" + recipeId + "' не существует"));

                if (recipe.getOwnerId() != null && !recipe.getOwnerId().equals(userId)) {
                    throw new MissingException("Рецепт с id '" + recipeId + "' не существует");
                }

                String error = validateRecipe(recipe, userRestrictions);
                if (!"OK".equals(error)) {
                    throw new MissingException("Рецепт '" + recipe.getName() + "' не подходит: " + error);
                }

                collectIngredients(totalRequirements, recipe, itemDto.getPortions(), recipe.getServing());

                newItem = new PlanItem(recipe, itemDto.getPortions());
            } else if (itemDto.getProductId() != null) {

                Optional.ofNullable(itemDto.getUnit())
                        .orElseThrow(() -> new MissingException("Для продукта должна быть указана единица измерения"));

                ProductDto product = circuitBreakerGetProductsById(itemDto.getProductId(), userId);

                addProductToRequirements(totalRequirements, product.getName(), itemDto.getPortions(), itemDto.getUnit());

                newItem = new PlanItem(itemDto.getProductId(), itemDto.getUnit(), itemDto.getPortions());
            } else {

                throw new MissingException("Элемент плана должен содержать либо recipeId, либо productId");
            }

            tempPlanItems.add(newItem);
        }

        checkTotalAvailability(userId, totalRequirements);

        for (PlanItem tempItem : tempPlanItems) {

            receivedEatingPlan.getPlanItems().add(tempItem);
            tempItem.setEatingPlan(receivedEatingPlan);
        }

        eatingPlanRepository.save(receivedEatingPlan);
        subtractIngredientsFromInventory(receivedEatingPlan, userId);

        return eatingPlanMapper.toDto(receivedEatingPlan);
    }


    @Transactional
    @Override
    public EatingPlanDto updateEatingPlan(int id, int userId, EatingPlanDto eatingPlanDto) {
        circuitBreakerUserExists(userId);

        EatingPlan existingPlan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("План питания с id '" + id + "' не найден у пользователя c id '" + userId + "'"));

        if (!eatingPlanDto.getType().equals(EatingType.SNACK) && eatingPlanRepository.existsByDateAndTypeAndUserIdAndIdNot(eatingPlanDto.getDate(), eatingPlanDto.getType(),userId, id)) {
            throw new ExistsException("План питания на " + eatingPlanDto.getDate() + ": " + eatingPlanDto.getType() + " существует для пользователя с id '" + userId +"'");
        }

        Status currentStatus = existingPlan.getStatus();
        if (currentStatus.equals(Status.IN_PROGRESS) || currentStatus.equals(Status.CONSUMED)) {
            throw new ExistsException("Нельзя изменить план, который уже готовится или был съеден. Отмените его или создайте новый.");
        }

        existingPlan.setDate(eatingPlanDto.getDate());
        existingPlan.setType(eatingPlanDto.getType());

        if (existingPlan.getPlanItems() != null && !existingPlan.getPlanItems().isEmpty()) {
            returnIngredientsToInventory(existingPlan, userId);
        }

        List<PlanItemDto> newItems = eatingPlanDto.getItems();

        Optional.ofNullable(newItems)
                .orElseThrow(() -> new MissingException("План должен содержать хотя бы одно блюдо"));

        UserRestrictionsDto user = circuitBreakerGetUserRestrictions(userId);

        for (PlanItemDto itemDto : newItems) {
            Recipe recipe = recipeRepository.findById(itemDto.getRecipeId())
                    .orElseThrow(() -> new MissingException("Рецепт не найден"));

            String error = validateRecipe(recipe, user);
            if (!"OK".equals(error)) {
                throw new MissingException("Рецепт '" + recipe.getName() + "' не подходит: " + error);
            }

            checkAvailability(userId, recipe, itemDto.getPortions(), recipe.getServing());
            existingPlan.addPlanItem(recipe, itemDto.getPortions());
        }

        if (existingPlan.getStatus() == Status.CANCELLED) {
            existingPlan.setStatus(Status.CREATED);
        }

        subtractIngredientsFromInventory(existingPlan, userId);
        eatingPlanRepository.save(existingPlan);

        return eatingPlanMapper.toDto(existingPlan);
    }

    @Transactional
    @Override
    public void deleteEatingPlan(int id, int userId) {
        circuitBreakerUserExists(userId);

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
        circuitBreakerUserExists(userId);

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
        circuitBreakerUserExists(userId);

        EatingPlan plan = eatingPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new MissingException("План питания с id '" + planId + "' не существует"));

        Map<String, ProductStatusDto> totalRequirements = new HashMap<>();
        for (PlanItem item : plan.getPlanItems()) {
            Recipe recipe = item.getRecipe();
            int portions = item.getPortions();
            int recipeServings = recipe.getServing();

            for (RecipeIngredient ing : recipe.getIngredients()) {
                double needed = (ing.getQuantity() / recipeServings) * portions;

                String key = ing.getName().toLowerCase();
                if (totalRequirements.containsKey(key)) {

                    ProductStatusDto existing = totalRequirements.get(key);
                    existing.setRequiredAmount(existing.getRequiredAmount() + needed);
                } else {

                    ProductStatusDto dto = createProductStatusDto(ing, needed);
                    totalRequirements.put(key, dto);
                }
            }
        }

        List<String> ingredientsNames = totalRequirements.values().stream().map(ProductStatusDto::getName).toList();
        List<ProductStatusDto> allStatuses = circuitBreakerGenerateShoppingList(ingredientsNames, userId);

        List<ProductStatusDto> needToBuy = new ArrayList<>();
        for (ProductStatusDto item : totalRequirements.values()) {
            ProductStatusDto stockItem = allStatuses.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(item.getName()))
                    .findFirst()
                    .orElse(null);

            double hasAmount = 0.0;

            if (stockItem != null && stockItem.getUnit() != null) {
                item.setUnit(stockItem.getUnit());
            }

            item.setAvailableAmount(hasAmount);

            double toBuy = Math.max(0, item.getRequiredAmount() - hasAmount);
            item.setToBuyAmount(toBuy);

            if (toBuy > 0) {
                needToBuy.add(item);
            }
        }

        return createShoppingList(needToBuy);
    }

    @Override
    public boolean existEatingPlanWithRecipe(int id, int recipeId, int userId) {
        circuitBreakerUserExists(userId);

        EatingPlan plan = eatingPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MissingException("План c id '" + id +"' не найден для пользователя с id '" + userId + "'"));

        if (plan.getPlanItems() == null) {
            return false;
        }

        return plan.getPlanItems().stream()
                .anyMatch(item -> item.getRecipe().getId() == recipeId);
    }

    @Override
    public List<RecipeDto> generateRecipes(int userId){
        circuitBreakerUserExists(userId);

        UserRestrictionsDto user = circuitBreakerGetUserRestrictions(userId);

        List<Recipe> existingRecipes = recipeRepository.findByOwnerIdOrOwnerIdIsNull(userId);
        List<Recipe> filteredRecipes = filterRecipes(existingRecipes, user);
        List<RecipeDto> newRecipes;
        newRecipes = filteredRecipes.stream().map(recipeMapper::toDto).toList();

        return newRecipes ;
    }

    private void subtractIngredientsFromInventory(EatingPlan plan, int userId) {
        for (PlanItem item : plan.getPlanItems()) {
            if (item.getRecipe() != null) {
                Recipe recipe = item.getRecipe();
                int portions = item.getPortions();
                int recipeServings = recipe.getServing();

                for (RecipeIngredient ingredient : recipe.getIngredients()) {
                    double realAmountUsed = (ingredient.getQuantity() / recipeServings) * portions;

                    ConsumeProductDto product = createConsumeRecipeDto(userId, ingredient, realAmountUsed);
                    circuitBreakerConsumeProduct(product);
                }
            } else {

                ConsumeProductDto productDto = createConsumeProductDto(userId, item);

                circuitBreakerConsumeProduct(productDto);
            }
        }
    }

    private ProductStatusDto createProductStatusDto(RecipeIngredient ingredient, double needed) {
        ProductStatusDto product = new ProductStatusDto();
        product.setName(ingredient.getName());
        product.setUnit(ingredient.getUnit());
        product.setRequiredAmount(needed);
        product.setAvailable(false);
        product.setAvailableAmount(0.0);

        return product;
    }

    private ShoppingListDto createShoppingList(List<ProductStatusDto> needToBuy) {
        ShoppingListDto list = new ShoppingListDto();
        list.setNeedToBuy(needToBuy);

        return list;
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


    private void checkAvailability(int userId, Recipe recipe, int portions, int recipeServings) {
        List<String> ingredients = getIngredientNames(recipe);

        List<ProductStatusDto> statusList = circuitBreakerGenerateShoppingList(ingredients, userId);

        Optional.ofNullable(statusList)
                .orElseThrow(() -> new MissingException("Невозможно проверить наличие продуктов: сервис склада недоступен"));

        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            if (ingredient.getQuantity() <= 0) {
                continue;
            }

            double neededAmount = (ingredient.getQuantity() / (double) recipeServings) * portions;
            Measure neededUnit = ingredient.getUnit();

            ProductStatusDto stockItem = statusList.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(ingredient.getName()))
                    .findFirst()
                    .orElse(null);

            Optional.ofNullable(stockItem)
                    .orElseThrow(() -> new MissingException("Ингредиент '" + ingredient.getName() + "' не найден на складе"));

            if (!stockItem.isAvailable()) {
                throw new MissingException("Ингредиент '" + ingredient.getName() + "' недоступен: " +
                        (stockItem.getAvailableAmount() == 0 ? "нет на складе" : "истек срок годности"));
            }

            double availableAmountInRecipeUnit = convertAmount(stockItem.getAvailableAmount(), stockItem.getUnit(), neededUnit);

            if (availableAmountInRecipeUnit < neededAmount - 0.001) {
                throw new MissingException(String.format("Недостаточно продукта '" + ingredient.getName() + "'. Требуется: " + neededAmount  + " " + neededUnit + ", доступно: " + availableAmountInRecipeUnit + " " + neededUnit));
            }
        }
    }

    private void collectIngredients(Map<String, ProductRequirementDto> totalRequirements, Recipe recipe, int portions, int servings) {
        for (RecipeIngredient ingredient : recipe.getIngredients()) {

            double needed = (ingredient.getQuantity() / (double) servings) * portions;
            addProductToRequirements(totalRequirements, ingredient.getName(), needed, ingredient.getUnit());
        }
    }

    private void addProductToRequirements(Map<String, ProductRequirementDto> totalRequirements, String name, double amount, Measure unit) {
        String key = name.toLowerCase();

        double baseAmount = convertToBase(amount, unit);
        Measure baseUnit = getBaseUnit(unit);

        if (totalRequirements.containsKey(key)) {
            ProductRequirementDto requirementProduct = totalRequirements.get(key);

            if (!requirementProduct.getUnit().equals(baseUnit)) {

                throw new IllegalArgumentException("Несовместимые единицы измерения для продукта " + name);
            }

            requirementProduct.setRequiredAmount(requirementProduct.getRequiredAmount() + baseAmount);
        } else {

            ProductRequirementDto product = createProductRequirementDto(name, baseAmount, baseUnit);
            totalRequirements.put(key, product);
        }
    }

    private ProductRequirementDto createProductRequirementDto(String name, double needed, Measure unit) {
        ProductRequirementDto product = new ProductRequirementDto();

        product.setName(name);
        product.setRequiredAmount(needed);
        product.setUnit(unit);

        return product;
    }

    private double convertToBase(double value, Measure unit) {
        if (unit == null) {
            return value;
        }

        return switch (unit) {
            case KG, L -> value * 1000.0;
            case TSP -> value * 5.0;
            case TBSP -> value * 15.0;
            case CUP -> value * 240.0;
            default -> value;
        };
    }

    private Measure getBaseUnit(Measure unit) {
        return switch (unit) {
            case KG, G, TSP, TBSP, CUP, PINCH, PACKET -> Measure.G;
            case L, ML -> Measure.ML;
            case PCS -> Measure.PCS;
        };
    }

    private void checkTotalAvailability(int userId, Map<String, ProductRequirementDto> totalRequirements) {
        if (totalRequirements.isEmpty()) {
            return;
        }

        List<String> names = totalRequirements.values().stream()
                .map(ProductRequirementDto::getName)
                .distinct()
                .toList();

        List<ProductStatusDto> stockStatuses = circuitBreakerGenerateShoppingList(names, userId);

        for (ProductRequirementDto requirementProduct : totalRequirements.values()) {
            ProductStatusDto stock = stockStatuses.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(requirementProduct.getName()))
                    .findFirst()
                    .orElse(null);

            Optional.ofNullable(stock)
                    .orElseThrow(() -> new MissingException("Продукт '" + requirementProduct.getName() + "' недоступен"));

            if (!stock.isAvailable()) {

                throw new MissingException("Продукт '" + requirementProduct.getName() + "' недоступен");
            }

            if (stock.getAvailableAmount() == 0) {

                throw new MissingException("Продукта '" + requirementProduct.getName() + "' нет на складе");
            }

            double available = convertAmount(stock.getAvailableAmount(), stock.getUnit(), requirementProduct.getUnit());

            if (available < requirementProduct.getRequiredAmount() - 0.001) {

                throw new MissingException(String.format("Недостаточно продукта '%s' для всего плана. Требуется: %.2f %s, доступно: %.2f %s", requirementProduct.getName(), requirementProduct.getRequiredAmount(), requirementProduct.getUnit(), available, requirementProduct.getUnit()));
            }
        }
    }

    private double convertAmount(double amount, Measure from, Measure to) {
        if (from == to) {
            return amount;
        }

        double baseAmount;
        switch (from) {
            case L, KG -> baseAmount = amount * 1000.0;
            case TSP -> baseAmount = amount * 5.0;
            case TBSP -> baseAmount = amount * 15.0;
            case CUP -> baseAmount = amount * 240.0;
            case PCS, ML, G, PINCH, PACKET -> baseAmount = amount;
            default -> baseAmount = amount;
        }

        switch (to) {
            case L -> {
                if (isWeight(from)) throw new IllegalArgumentException("Нельзя конвертировать вес в объем");
                return baseAmount / 1000.0;
            }
            case ML -> {
                if (isWeight(from)) throw new IllegalArgumentException("Нельзя конвертировать вес в объем");
                return baseAmount;
            }
            case KG -> {
                if (isVolume(from)) throw new IllegalArgumentException("Нельзя конвертировать объем в вес");
                return baseAmount / 1000.0;
            }
            case G -> {
                if (isVolume(from)) throw new IllegalArgumentException("Нельзя конвертировать объем в вес");
                return baseAmount;
            }
            case PCS -> {
                if (isWeight(from) || isVolume(from)) throw new IllegalArgumentException("Нельзя конвертировать вес/объем в штуки");
                return baseAmount;
            }
            case TSP -> {
                return baseAmount / 5.0;
            }
            case TBSP -> {
                return baseAmount / 15.0;
            }
            case CUP -> {
                return baseAmount / 240.0;
            }
            default -> {
                return baseAmount;
            }
        }
    }

    private boolean isWeight(Measure m) {
        return m == Measure.G || m == Measure.KG || m == Measure.TSP || m == Measure.TBSP || m == Measure.CUP || m == Measure.PINCH || m == Measure.PACKET;
    }

    private boolean isVolume(Measure m) {
        return m == Measure.ML || m == Measure.L;
    }

    private void returnIngredientsToInventory(EatingPlan plan, int userId) {
        for (PlanItem item : plan.getPlanItems()) {
            if (item.getRecipe() != null) {
                Recipe recipe = item.getRecipe();
                int portions = item.getPortions();
                int recipeServings = recipe.getServing();

                for (RecipeIngredient ingredient : recipe.getIngredients()) {

                    double realAmountUsed = (ingredient.getQuantity() / recipeServings) * portions;

                    ConsumeProductDto dto = createConsumeRecipeDto(userId, ingredient, realAmountUsed);

                    circuitBreakerReturnProduct(dto);
                }
            } else {

                ConsumeProductDto productDto = createConsumeProductDto(userId, item);

                circuitBreakerReturnProduct(productDto);
            }
        }
    }

    private ConsumeProductDto createConsumeRecipeDto(int userId, RecipeIngredient ingredient, double realAmountUsed) {
        ConsumeProductDto dto = new ConsumeProductDto();
        dto.setUserId(userId);
        dto.setProductName(ingredient.getName());
        dto.setAmount(realAmountUsed);
        dto.setUnit(ingredient.getUnit());

        return dto;
    }

    private ConsumeProductDto createConsumeProductDto(int userId,PlanItem item) {
        ProductDto product = circuitBreakerGetProductsById(item.getProductId(), userId);

        ConsumeProductDto dto = new ConsumeProductDto();
        dto.setUserId(userId);
        dto.setProductId(item.getProductId());
        dto.setProductName(product.getName());
        dto.setAmount(item.getPortions());
        dto.setUnit(item.getUnit());

        return dto;
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

    private void circuitBreakerReturnProduct(ConsumeProductDto product) {
        executeWithCircuitBreakerVoid(() -> inventoryClient.returnProduct(product));
    }

    private void circuitBreakerConsumeProduct(ConsumeProductDto product) {
        executeWithCircuitBreakerVoid(() -> inventoryClient.consumeProduct(product));
    }

    private List<ProductStatusDto> circuitBreakerGenerateShoppingList(List<String> ingredientsNames, int userId) {

        return executeWithCircuitBreaker("inventoryService", () -> inventoryClient.generateShoppingList(userId, ingredientsNames));
    }

    private ProductDto circuitBreakerGetProductsById(int productId, int userId) {
        return executeWithCircuitBreaker("inventoryService", () -> inventoryClient.getProductsById(userId, productId));
    }

    private void executeWithCircuitBreakerVoid(Runnable runnable) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("inventoryService");
        Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(cb, runnable);

        try {
            decoratedRunnable.run();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit Breaker inventoryService разомкнут. Сервис недоступен");
            throw new MissingException("InventoryService временно недоступен");
        } catch (MissingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при вызове сервиса через CB 'inventoryService': {}", e.getMessage(), e);
            throw new MissingException("Ошибка связи с inventoryService");
        }
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
