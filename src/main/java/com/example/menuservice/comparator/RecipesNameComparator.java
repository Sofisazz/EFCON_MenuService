package com.example.menuservice.comparator;

import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.RecipeIngredientDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RecipesNameComparator implements Comparator<RecipeDto> {

    private final Set<String> expiringNames;

    @Override
    public int compare(RecipeDto r1, RecipeDto r2) {
        int count1 = countMatches(r1);
        int count2 = countMatches(r2);

        return Integer.compare(count2, count1);
    }

    private int countMatches(RecipeDto recipe) {
        if (recipe.getIngredients() == null) {
            return 0;
        }

        int count = 0;
        for (RecipeIngredientDto ingredient : recipe.getIngredients()) {
            if (expiringNames.contains(ingredient.getName())) {
                count++;
            }
        }

        return count;
    }
}
