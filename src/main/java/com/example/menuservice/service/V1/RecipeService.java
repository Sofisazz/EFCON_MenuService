package com.example.menuservice.service.V1;

import com.example.menuservice.dto.RecipeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public interface RecipeService {

    Page<RecipeDto> findAllRecipes(Pageable pageable);
    RecipeDto findRecipeById(int id);
    RecipeDto createRecipe(RecipeDto recipeDto);
    RecipeDto updateRecipe(int id, RecipeDto recipeDto);
    void deleteRecipeById(int id);
}
