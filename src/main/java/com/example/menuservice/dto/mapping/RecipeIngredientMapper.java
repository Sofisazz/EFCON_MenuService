package com.example.menuservice.dto.mapping;

import com.example.menuservice.dto.RecipeIngredientDto;
import com.example.menuservice.entity.RecipeIngredient;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecipeIngredientMapper {
    RecipeIngredient toEntity(RecipeIngredientDto recipeIngredientDto);
    RecipeIngredientDto toDto(RecipeIngredient recipeIngredient);
}
