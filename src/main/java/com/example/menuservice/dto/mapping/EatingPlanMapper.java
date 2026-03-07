package com.example.menuservice.dto.mapping;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.entity.EatingPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {RecipeMapper.class})
public interface EatingPlanMapper {

    EatingPlan toEntity(EatingPlanDto eatingPlanDto);

    @Mapping(target = "recipeId", expression = "java(eatingPlan.getRecipe().getId())")
    EatingPlanDto toDto(EatingPlan eatingPlan);

    @Mapping(target = "id",ignore = true)
    @Mapping(target = "recipe", ignore = true)
    void updateFromDto(EatingPlanDto eatingPlanDto, @MappingTarget EatingPlan eatingPlan);
}
