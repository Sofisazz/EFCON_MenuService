package com.example.menuservice.comparator;

import com.example.menuservice.entity.Recipe;
import com.example.menuservice.entity.RecipeIngredient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RecipeExpiringComparator implements Comparator<Recipe> {

    private final Map<String, Integer> expiringIngredientsScore;

    @Override
    public int compare(Recipe r1, Recipe r2) {
        int score1 = calculateTotalScore(r1);
        int score2 = calculateTotalScore(r2);

        return Integer.compare(score2, score1);
    }

    private int calculateTotalScore(Recipe recipe) {
        if (recipe.getIngredients() == null || expiringIngredientsScore.isEmpty()) {
            return 0;
        }

        int totalScore = 0;
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            if (ingredient.getName() != null) {
                String nameLower = ingredient.getName().toLowerCase();

                if (expiringIngredientsScore.containsKey(nameLower)) {
                    totalScore += expiringIngredientsScore.get(nameLower);
                }
            }
        }
        return totalScore;
    }
}