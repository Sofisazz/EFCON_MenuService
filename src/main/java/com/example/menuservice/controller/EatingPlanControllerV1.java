package com.example.menuservice.controller;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.enums.Status;
import com.example.menuservice.service.EatingPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/plans")
public class EatingPlanControllerV1 {

    private final EatingPlanService eatingPlanService;

    @GetMapping()
    public Page<EatingPlanDto> getAllEatingPlans(@RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
                                                 @RequestParam(value = "limit", defaultValue = "5") @Min(1) @Max(100) Integer limit) {
        return eatingPlanService.findAllEatingPlans(PageRequest.of(offset, limit));
    }

    @GetMapping("/{id}")
    public EatingPlanDto getEatingPlan(@PathVariable int id){
        return eatingPlanService.findEatingPlanById(id);
    }

    @PostMapping()
    public EatingPlanDto createEatingPlan(@Valid @RequestBody EatingPlanDto eatingPlanDto) {
        return eatingPlanService.createEatingPlan(eatingPlanDto);
    }

    @PutMapping("/{id}")
    public EatingPlanDto changeEatingPlan(@PathVariable int id, @Valid @RequestBody EatingPlanDto eatingPlanDto) {
        return eatingPlanService.updateEatingPlan(id, eatingPlanDto);
    }

    @DeleteMapping("/{id}")
    public void DeleteEatingPlan(@PathVariable int id) {
        eatingPlanService.deleteEatingPlanById(id);
    }

    @PatchMapping("/{id}/status")
    public EatingPlanDto changeStatusEatingPlan(@PathVariable int id, @RequestParam Status status) {
        return eatingPlanService.updateStatusEatingPlan(id, status);
    }

    @GetMapping("/{id}/recipe")
    public RecipeDto getRecipeForPlan(@PathVariable int id) {
        return eatingPlanService.findRecipeForPlan(id);
    }
}
