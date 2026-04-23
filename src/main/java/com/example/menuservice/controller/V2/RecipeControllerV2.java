package com.example.menuservice.controller.V2;


import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.ShoppingListDto;
import com.example.menuservice.service.V2.RecipeServiceV2;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v2/recipes")
public class RecipeControllerV2 {

    private final RecipeServiceV2 recipeService;


    @GetMapping()
    public List<RecipeDto> getAllRecipes(@RequestParam int userId) {
        return recipeService.getAllRecipes(userId);
    }

    @GetMapping("/{id}")
    RecipeDto getRecipeById(@PathVariable int id,
                            @RequestParam int userId) {
        return recipeService.getRecipeById(id ,userId);
    }

    @GetMapping("/restrictions")
    public List<RecipeDto> getRecipesAccordinfRestrictions(@RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
                                                           @RequestParam(value = "limit", defaultValue = "5") @Min(1) @Max(100) Integer limit,
                                                           @RequestParam int userId) {
        return recipeService.generateRecipesAccordingRestrictions(PageRequest.of(offset, limit), userId);
    }

    @GetMapping("/{recipeId}/shopping-list")
    public ShoppingListDto getShoppingList(@PathVariable int recipeId,
                                           @RequestParam int userId) {
        return recipeService.getShoppingListForRecipe(recipeId, userId);
    }

    @PostMapping()
    public RecipeDto createRecipe(@Valid @RequestBody RecipeDto recipeDto,
                                  @RequestParam(required = false) Integer userId) {
        return recipeService.createRecipeV2(recipeDto, userId);
    }


    @GetMapping("/expiring")
    public List<RecipeDto> getExpiringRecipes(@RequestParam int userId) {
        return recipeService.getRecipesSortedByExpiringIngredients(userId);
    }

    @PutMapping("/{id}")
    public RecipeDto changeRecipe(@PathVariable int id,
                                  @Valid @RequestBody RecipeDto recipeDto,
                                  @RequestParam(required = false) Integer userId) {
        return recipeService.updateRecipeV2(id, recipeDto, userId);
    }

    @DeleteMapping("/{id}")
    public void DeleteRecipe(@PathVariable int id,
                             @RequestParam(required = false) Integer userId) {
        recipeService.deleteRecipeByIdV2(id, userId);
    }

    @GetMapping("/search")
    public List<RecipeDto> searchRecipesByName(@RequestParam String name,
                                               @RequestParam int userId) {

        return recipeService.searchRecipesByName(name, userId);
    }
}
