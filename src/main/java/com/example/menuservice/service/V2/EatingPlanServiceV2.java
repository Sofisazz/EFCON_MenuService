package com.example.menuservice.service.V2;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.ShoppingListDto;
import com.example.menuservice.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;


public interface EatingPlanServiceV2 {
    EatingPlanDto createEatingPlanForUser(int userId, EatingPlanDto eatingPlanDto);
    Page<EatingPlanDto> findAllEatingPlansForUser(PageRequest pageable, int userId);
    EatingPlanDto findEatingPlanByIdForUser(int id, int userId);

    boolean existEatingPlanByIdForUser(int id, int userId);
    EatingPlanDto updateEatingPlan(int id, int userId, EatingPlanDto updateDto);

    void deleteEatingPlan(int id, int userId);

    EatingPlanDto updateStatusEatingPlan(int id, int userId, Status status);

    ShoppingListDto getShoppingListForPlan(int planId, int userId);

    boolean existEatingPlanWithRecipe(int id, int recipeId, int userId);

    List<RecipeDto> generateRecipes(int userId);
}
