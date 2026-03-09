package com.example.menuservice.service.serviceImpl;

import com.example.menuservice.comparator.RecipesComparator;
import com.example.menuservice.dto.KafkaProductDto;
import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.exceptions.MissingException;
import com.example.menuservice.repository.RecipeRepository;
import com.example.menuservice.service.KafkaConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KafkaConsumerServiceImpl implements KafkaConsumerService {

    private final RecipeRepository recipeRepository;
    private final RecipeMapper recipeMapper;
    private List<KafkaProductDto> cache;

    @KafkaListener(topics = "expiring_date_products", groupId = "expiring-group")
    @SuppressWarnings("unused")
    public void consumeExpiringProducts(List<KafkaProductDto> products) {
        this.cache = Optional.ofNullable(products)
                .orElse(new ArrayList<>());

        System.out.println(products);
    }

    @Override
    public List<RecipeDto> getExpiringProducts() {
            Optional.ofNullable(cache).orElseThrow(() -> new MissingException("Нет продуктов с истекшим сроком годности"));

        Set<String> expiringNames = new HashSet<>();
        for (KafkaProductDto product : cache) {
            String name = product.getName();
            expiringNames.add(name);
        }

        List<RecipeDto> allRecipes = recipeRepository.findAll().stream().map(recipeMapper::toDto).toList();
        if (allRecipes.isEmpty()) {
            throw new MissingException("Нет рецептов в базе данных");
        }

        List<RecipeDto> matchingRecipes = new ArrayList<>();
        for (RecipeDto recipe : allRecipes) {
            boolean hasMatch = false;

            for (String ingredient : recipe.getIngredients()) {
                if (!hasMatch) {
                    if (expiringNames.contains(ingredient)) {
                        hasMatch = true;
                    }
                }
            }

            if (hasMatch) {
                matchingRecipes.add(recipe);
            }
        }

        matchingRecipes.sort(new RecipesComparator(expiringNames));

        return matchingRecipes;
    }

    @Override
    public List<KafkaProductDto> getCachedData() {
        return cache;
    }
}