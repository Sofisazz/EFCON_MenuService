package com.example.menuservice.dto.mapping;

import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.entity.Recipe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {EatingPlanMapper.class, RecipeIngredientMapper.class})
public interface RecipeMapper {

    Recipe toEntity(RecipeDto recipeDto);
    RecipeDto toDto(Recipe recipe);

    @Mapping(target = "id",ignore = true)
    @Mapping(target = "ingredients", ignore = true)
    void updateFromDto(RecipeDto recipeDto, @MappingTarget Recipe recipe);
}
