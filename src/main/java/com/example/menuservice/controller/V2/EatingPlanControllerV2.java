package com.example.menuservice.controller.V2;

import com.example.menuservice.dto.EatingPlanDto;
import com.example.menuservice.dto.ShoppingListDto;
import com.example.menuservice.enums.Status;
import com.example.menuservice.service.V2.EatingPlanServiceV2;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v2/plans")
public class EatingPlanControllerV2 {

    private final EatingPlanServiceV2 eatingPlanService;

    @GetMapping()
    public Page<EatingPlanDto> getAllEatingPlans(@RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
                                                 @RequestParam(value = "limit", defaultValue = "5") @Min(1) @Max(100) Integer limit,
                                                 @RequestParam int userId) {
        return eatingPlanService.findAllEatingPlansForUser(PageRequest.of(offset, limit), userId);
    }

    @GetMapping("/{id}")
    public EatingPlanDto getEatingPlan(@PathVariable int id,
                                       @RequestParam int userId){
        return eatingPlanService.findEatingPlanByIdForUser(id, userId);
    }

    @GetMapping("/{id}/exist")
    public boolean existEatingPlan(@PathVariable int id,
                                   @RequestParam int userId){
        return eatingPlanService.existEatingPlanByIdForUser(id, userId);
    }

    @GetMapping("/{id}/recipes/{recipeId}/exist")
    public boolean existEatingPlanWithRecipe(@PathVariable int id,
                                   @PathVariable int recipeId,
                                   @RequestParam int userId){
        return eatingPlanService.existEatingPlanWithRecipe(id, recipeId,userId);
    }

    @GetMapping("/{planId}/shopping-list")
    public ShoppingListDto getShoppingList(@PathVariable int planId,
                                           @RequestParam int userId) {
        return eatingPlanService.getShoppingListForPlan(planId, userId);
    }

    @PostMapping()
    public EatingPlanDto createEatingPlan(@Valid @RequestBody EatingPlanDto eatingPlanDto,
                                          @RequestParam int userId) {
        return eatingPlanService.createEatingPlanForUser(userId, eatingPlanDto);
    }

    @PutMapping("/{id}")
    public EatingPlanDto changeEatingPlan(@PathVariable int id,
                                          @RequestParam int userId,
                                          @Valid @RequestBody EatingPlanDto eatingPlanDto) {
        return eatingPlanService.updateEatingPlan(id, userId, eatingPlanDto);
    }

    @PatchMapping("/{id}/status")
    public EatingPlanDto changeStatusEatingPlan(@PathVariable int id,
                                                @RequestParam int userId,
                                                @RequestParam Status status) {
        return eatingPlanService.updateStatusEatingPlan(id, userId, status);
    }

    @DeleteMapping("/{id}")
    public void DeleteEatingPlan(@PathVariable int id,
                                 @RequestParam int userId) {
        eatingPlanService.deleteEatingPlan(id, userId);
    }
}
