package com.example.menuservice.dto.mapping;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.dto.PlanItemDto;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.PlanItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", uses = {RecipeMapper.class})
public interface EatingPlanMapper {

    @Mapping(target = "planItems", ignore = true)
    @Mapping(target = "userId", ignore = true)
    EatingPlan toEntity(EatingPlanDto eatingPlanDto);

    @Mapping(target = "items", source = "planItems")
    EatingPlanDto toDto(EatingPlan eatingPlan);

    @Mapping(target = "planItems", ignore = true)
    @Mapping(target = "id",ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateFromDto(EatingPlanDto eatingPlanDto, @MappingTarget EatingPlan eatingPlan);

    default List<PlanItemDto> mapPlanItemsToDto(List<PlanItem> planItems) {
        if (planItems == null) {
            return new ArrayList<>();
        }

        List<PlanItemDto> dtos = new ArrayList<>();
        for (PlanItem item : planItems) {

            PlanItemDto dto = new PlanItemDto();
            if (item.getRecipe() != null) {

                dto.setRecipeId(item.getRecipe().getId());
            }  else if (item.getProductId() != null) {

                dto.setProductId(item.getProductId());
                dto.setUnit(item.getUnit());
            }

            dto.setPortions(item.getPortions());
            dtos.add(dto);
        }
        return dtos;
    }
}
