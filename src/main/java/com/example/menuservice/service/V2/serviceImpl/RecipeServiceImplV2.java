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
import com.example.menuservice.service.V2.OllamaService;
import com.example.menuservice.service.V2.RecipeServiceV2;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

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
    private final RestClient restClient;

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final AllergenMap allergenMap;

    private final OllamaService ollamaService;

    @Value("${themealdb.urlByIngredient}")
    private String urlByIngredient;

    @Value("${themealdb.urlById}")
    private String urlById;

    @Value("${calorieninjas.url}")
    private String calorieUrl;

    @Value("${calorieninjas.key}")
    private String calorieKey;

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

    @Transactional
    @Override
    public Page<RecipeDto> findRecipesByIngredientsExternal(List<String> ingredients, int userId, Pageable pageable) {
        circuitBreakerUserExists(userId);

        if (ingredients == null || ingredients.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<Recipe> localRecipes = new HashSet<>();
        for (String ing : ingredients) {
            List<Recipe> foundRecipes = recipeRepository.findByIngredientsNameIgnoreCaseAndOwnerId(ing, userId);
            localRecipes.addAll(foundRecipes);
        }
        List<RecipeDto> localRecipesDtos = localRecipes.stream().map(recipeMapper::toDto).toList();

        Set<String> allFoundRecipeIds = new HashSet<>();
        try {
            for (String ing : ingredients) {
                String translatePrompt = "Translate this food ingredient from Russian to English. Return ONLY the English word. Ingredient: " + ing;
                String enIng = ollamaService.generateResponse(translatePrompt);
                if (enIng == null) {
                    enIng = ing;
                }

                JsonNode searchResponse = restClient.get()
                        .uri(urlByIngredient + enIng)
                        .retrieve()
                        .body(JsonNode.class);


                if (searchResponse != null && searchResponse.has("meals")) {
                    JsonNode meals = searchResponse.get("meals");

                    if (!meals.isNull()) {
                        for (JsonNode meal : meals) {
                            String id = meal.path("idMeal").asText();
                            if (!id.isEmpty()) {
                                allFoundRecipeIds.add(id);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {

            return new PageImpl<>(localRecipesDtos, pageable, localRecipesDtos.size());
        }

        List<String> finalExternalIds = new ArrayList<>(allFoundRecipeIds);

        int limit = ingredients.size() * 12; // т к долго грузит из-за большого кол-ва рецептов, чтобы было по типу заглушки

        if (finalExternalIds.size() > limit) {

            finalExternalIds = finalExternalIds.subList(0, limit);
        }

        List<RecipeDto> externalRecipesDtos = finalExternalIds.parallelStream()
                .map(recipeId -> {
                    try {
                        RecipeDto dto = getExternalRecipes(recipeId, userId, ingredients);
                        if (dto != null) {
                            return dto;
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при обработке рецепта {}: {}", recipeId, e.getMessage());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<RecipeDto> allValidatedDtos = new ArrayList<>(localRecipesDtos);
        allValidatedDtos.addAll(externalRecipesDtos);

        int totalElements = allValidatedDtos.size();
        int start = (int) pageable.getOffset();

        int end = Math.min(start + pageable.getPageSize(), totalElements);

        List<RecipeDto> pageContent;
        if (start >= totalElements) {

            pageContent = Collections.emptyList();
        } else {

            pageContent = allValidatedDtos.subList(start, end);
        }

        return new PageImpl<>(pageContent, pageable, totalElements);
    }

    @Override
    public List<RecipeDto> searchRecipesByName(String name, int userId) {
        circuitBreakerUserExists(userId);

        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }

        List<Recipe> recipesLocal = recipeRepository.findByNameContainingIgnoreCaseAndOwnerId(name, userId);
        List<Recipe> recipesGlobal = recipeRepository.findByNameContainingIgnoreCaseAndOwnerIdIsNull(name);

        List<Recipe> recipes = new ArrayList<>(recipesLocal);
        recipes.addAll(recipesGlobal);

        return recipes.stream().map(recipeMapper::toDto).toList();
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

    private RecipeDto getExternalRecipes(String recipeId, int userId, List<String> requiredIngredients) {
        try {
            JsonNode detailResponse = restClient.get()
                    .uri(urlById + recipeId)
                    .retrieve()
                    .body(JsonNode.class);

            if (detailResponse == null || !detailResponse.has("meals") || detailResponse.get("meals").isEmpty()) {
                return null;
            }

            log.info("Полученный продукт: {}", detailResponse);

            JsonNode mealData = detailResponse.get("meals").get(0);
            String ruName = getNameRecipe(mealData);

            Recipe createdRecipe = new Recipe();
            createdRecipe.setName(ruName);
            createdRecipe.setServing(1);
            createdRecipe.setOwnerId(userId);

            List<RecipeIngredient> recipeIngredients = new ArrayList<>();
            StringBuilder queryBuilder = new StringBuilder();

            for (int i = 1; i <= 20; i++) {
                String rawIng = mealData.path("strIngredient" + i).asText(null);
                String rawMeasure = mealData.path("strMeasure" + i).asText("");

                if (rawIng != null && !rawIng.trim().isEmpty()) {
                    String cleanIngNameEn = rawIng.trim();

                    String cleanIngNameRu;
                    try {

                        cleanIngNameRu = translateToRussian(cleanIngNameEn);

                    } catch (Exception e) {

                        cleanIngNameRu = cleanIngNameEn;
                    }

                    double quantity = 1.0;
                    Measure unit = Measure.PCS;

                    if (!rawMeasure.isBlank()) {

                        String lowerMeasure = rawMeasure.toLowerCase().trim();
                        String[] parts = lowerMeasure.split(" ");
                        unit = Measure.getMeasure(lowerMeasure);
                        quantity = calculateQuantity(parts);
                    }

                    RecipeIngredient recipeIngredient = new RecipeIngredient();
                    recipeIngredient.setName(cleanIngNameRu);
                    recipeIngredient.setQuantity(quantity);
                    recipeIngredient.setUnit(unit);
                    recipeIngredient.setRecipe(createdRecipe);
                    recipeIngredients.add(recipeIngredient);

                    String unitApi = (unit != Measure.PCS) ? unit.name().toLowerCase() : "";
                    queryBuilder.append(quantity).append(" ").append(unitApi).append(" ").append(cleanIngNameEn).append(", ");
                }
            }

            createdRecipe.setIngredients(recipeIngredients);

            Map<Integer, String> steps = getSteps(mealData);
            createdRecipe.setSteps(steps);

            String query = queryBuilder.length() > 2 ? queryBuilder.substring(0, queryBuilder.length() - 2) : "";
            if (!query.isEmpty()) {
                try {
                    JsonNode nutritionResponse = restClient.get()
                            .uri(calorieUrl + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8))
                            .header("X-Api-Key", calorieKey)
                            .retrieve()
                            .body(JsonNode.class);

                    if (nutritionResponse != null && nutritionResponse.has("items")) {
                        double totalCalories = 0, totalProteins = 0, totalFats = 0, totalCarbs = 0, totalWeight = 0;
                        for (JsonNode item : nutritionResponse.get("items")) {
                            totalCalories += item.path("calories").asDouble();
                            totalProteins += item.path("protein_g").asDouble();
                            totalFats += item.path("fat_total_g").asDouble();
                            totalCarbs += item.path("carbohydrates_total_g").asDouble();
                            totalWeight += item.path("serving_size_g").asDouble();
                        }

                        if (totalWeight > 0) {
                            createdRecipe.setCaloriesFor100(Math.round((totalCalories / totalWeight) * 100.0 * 100.0) / 100.0);
                            createdRecipe.setProteins(Math.round((totalProteins / totalWeight) * 100.0 * 100.0) / 100.0);
                            createdRecipe.setFats(Math.round((totalFats / totalWeight) * 100.0 * 100.0) / 100.0);
                            createdRecipe.setCarbohydrates(Math.round((totalCarbs / totalWeight) * 100.0 * 100.0) / 100.0);
                        }
                    }
                } catch (Exception e) {
                    log.error("Ошибка CalorieNinjas: {}", e.getMessage());
                }
            }

            RecipeDto tempRecipeDto = recipeMapper.toDto(createdRecipe);
            if (requiredIngredients.size() != 1) {

                if (!recipeContainsAllIngredients(tempRecipeDto, requiredIngredients)) {
                    return null;
                }
            }

            return tempRecipeDto;

        } catch (Exception e) {
            return null;
        }
    }

    private String getNameRecipe(JsonNode mealData){
        String enName = mealData.path("strMeal").asText();
        String ruName;

        try {
            String namePrompt = "Translate this food recipe name to Russian. Return ONLY the translation, no extra text: " + enName;
            ruName = ollamaService.generateResponse(namePrompt);
            if (ruName == null || ruName.isBlank()) {
                ruName = enName;
            }
        } catch (Exception e) {
            ruName = enName;
        }

        return ruName;
    }

    private double calculateQuantity(String[] parts) {
        if (parts.length == 0) {
            return 1.0;
        }

        String rawNum = parts[0].trim();

        try {

            if (rawNum.contains("/")) {
                String[] fraction = rawNum.split("/");
                if (fraction.length == 2) {

                    double numerator = Double.parseDouble(fraction[0].replaceAll("[^0-9.]", ""));
                    double denominator = Double.parseDouble(fraction[1].replaceAll("[^0-9.]", ""));
                    if (denominator != 0) {
                        return numerator / denominator;
                    }
                }
            }

            String cleanNum = rawNum.replaceAll("[^0-9.,]", "");

            cleanNum = cleanNum.replace(",", ".");

            if (!cleanNum.isEmpty()) {
                return Double.parseDouble(cleanNum);
            }

        } catch (NumberFormatException e) {
            log.warn("Не удалось распарсить количество из '{}': {}", rawNum, e.getMessage());
        }

        return 1.0;
    }

    private Map<Integer, String> getSteps(JsonNode mealData){
        String instructionsText = mealData.path("strInstructions").asText("");
        Map<Integer, String> steps = new LinkedHashMap<>();
        if (!instructionsText.isBlank()) {
            String[] rawSteps = instructionsText.split("\\r\\n\\r\\n|\\n\\n|\\r\\n|\\n");
            int stepNumber = 1;

            StringBuilder allStepsBuilder = new StringBuilder();
            List<String> stepList = new ArrayList<>();
            for (String rawStep : rawSteps) {
                String trimmed = rawStep.trim();
                if (!trimmed.isEmpty()) {
                    stepList.add(trimmed);
                    allStepsBuilder.append(stepNumber++).append(". ").append(trimmed).append("\n");
                }
            }

            try {
                String translateStepsPrompt = "Translate the following cooking steps to Russian. Keep the numbering. Return ONLY the translated text:\n" + allStepsBuilder;
                String translatedAll = ollamaService.generateResponse(translateStepsPrompt);

                if (translatedAll != null) {
                    String[] translatedLines = translatedAll.split("\n");
                    int idx = 0;
                    for (String line : translatedLines) {
                        if (!line.trim().isEmpty() && idx < stepList.size()) {
                            steps.put(idx + 1, line.trim());
                            idx++;
                        }
                    }
                    while (idx < stepList.size()) {
                        steps.put(idx + 1, stepList.get(idx));
                        idx++;
                    }
                } else {
                    throw new Exception("Empty response from Ollama");
                }
            } catch (Exception e) {
                log.warn("Ошибка перевода шагов, используем оригинал: {}", e.getMessage());
                for (int k = 0; k < stepList.size(); k++) {
                    steps.put(k + 1, stepList.get(k));
                }
            }
        }

        return steps;
    }

    private boolean recipeContainsAllIngredients(RecipeDto recipe, List<String> requiredIngredients) {
        if (recipe.getIngredients() == null) {
            return false;
        }

        List<String> recipeIngNames = recipe.getIngredients().stream()
                .map(RecipeIngredientDto::getName)
                .map(String::toLowerCase)
                .toList();

        for (String ruReqIng : requiredIngredients) {
            String ruReqIngLower = ruReqIng.toLowerCase().trim();

            boolean found = recipeIngNames.stream().anyMatch(name -> name.contains(ruReqIngLower) || ruReqIngLower.contains(name));

            if (!found) {

                String enReqIng = translateToEnglish(ruReqIng);
                if (!enReqIng.equals(ruReqIng)) {

                    String enReqIngLower = enReqIng.toLowerCase();
                    found = recipeIngNames.stream().anyMatch(name -> name.contains(enReqIngLower) || enReqIngLower.contains(name));
                }
            }

            if (!found) {
              return false;
            }
        }
        return true;
    }

    private String translateToEnglish(String ruIngredient) {
        String prompt = """
            Ты — переводчик кулинарных терминов.
            Переведи следующий ингредиент с русского на английский.
            Верни ТОЛЬКО одно слово или фразу на английском, без пояснений, кавычек, точек.
            
            Ингредиент: %s
            
            Перевод:""".formatted(ruIngredient);

        try {
            String result = ollamaService.generateResponse(prompt);
            if (result != null && !result.isBlank()) {

                return result.trim()
                        .replaceAll("[\"'.,;:!?\\[\\]{}()]", "")
                        .replaceAll("\\s+", " ");
            }
        } catch (Exception e) {
            log.warn("Ошибка перевода '{}': {}", ruIngredient, e.getMessage());
        }
        return ruIngredient;
    }

    private String translateToRussian(String enIngredient) {
        String prompt = """
            Ты — переводчик кулинарных терминов.
            Переведи следующий ингредиент с английского на русский.
            Верни ТОЛЬКО одно слово или фразу на русском, без пояснений, кавычек, точек.
            Если ингредиент не имеет прямого перевода, оставь оригинальное название.
            
            Ингредиент: %s
            
            Перевод:""".formatted(enIngredient);

        try {
            String result = ollamaService.generateResponse(prompt);
            if (result != null && !result.isBlank()) {

                String cleaned = result.trim()
                        .replaceAll("[\"'.,;:!?\\[\\]{}()]", "")
                        .replaceAll("\\s+", " ");

                if (cleaned.toLowerCase().matches(".*(system|instruction|mode|user|assistant|prompt|translate|return).*")) {

                    return enIngredient;
                }

                return cleaned;
            }
        } catch (Exception e) {
            log.warn("Ошибка перевода '{}': {}", enIngredient, e.getMessage());
        }
        return enIngredient;
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
                Optional<Recipe> receivedRecipe = recipeRepository.findByNameAndOwnerId(name, userId);
                if (receivedRecipe.isPresent() && receivedRecipe.get().getId() != id) {
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

            RecipeServiceImplV2.log.warn("Circuit Breaker '{}' разомкнут. Сервис недоступен", circuitBreakerName);
            throw new MissingException(circuitBreakerName + " временно недоступен");
        } catch (MissingException e) {

            throw e;
        } catch (Exception e) {

            RecipeServiceImplV2.log.error("Ошибка при вызове сервиса через CB '{}': {}", circuitBreakerName, e.getMessage(), e);
            throw new MissingException("Ошибка связи с " + circuitBreakerName);
        }
    }
}
