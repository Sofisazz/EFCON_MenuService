package com.example.menuservice.service.V2;

import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.ShoppingListDto;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface RecipeServiceV2 {


    ShoppingListDto getShoppingListForRecipe(int recipeId, int userId);

    RecipeDto createRecipeV2(RecipeDto recipeDto, Integer userId);

    void deleteRecipeByIdV2(int id, Integer userId);

    RecipeDto updateRecipeV2(int id, RecipeDto recipeDto, Integer userId);

    List<RecipeDto> getAllRecipes(int userId);

    RecipeDto getRecipeById(int id, int userId);

    List<RecipeDto> generateRecipesAccordingRestrictions(PageRequest pageable, int userId);

    List<RecipeDto> getRecipesSortedByExpiringIngredients(int userId);

    List<RecipeDto> searchRecipesByName(String name, int userId);
}
