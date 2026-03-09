package com.example.menuservice.service;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public interface EatingPlanService {

    Page<EatingPlanDto> findAllEatingPlans(Pageable pageable);
    EatingPlanDto findEatingPlanById(int id);
    EatingPlanDto createEatingPlan(EatingPlanDto eatingPlanDto);
    EatingPlanDto updateEatingPlan(int id, EatingPlanDto eatingPlanDto);
    void deleteEatingPlanById(int id);
    EatingPlanDto updateStatusEatingPlan(int id, Status status);
    RecipeDto findRecipeForPlan(int id);
}
