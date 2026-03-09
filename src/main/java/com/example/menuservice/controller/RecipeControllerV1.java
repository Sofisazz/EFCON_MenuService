package com.example.menuservice.controller;


import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.service.RecipeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeControllerV1 {

    private final RecipeService recipeService;

    @GetMapping()
    public Page<RecipeDto> getAllRecipes(@RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
                                         @RequestParam(value = "limit", defaultValue = "2") @Min(1) @Max(100) Integer limit,
                                         @RequestParam(value = "sortName", defaultValue = "name") String  sortName) {
        return recipeService.findAllRecipes(PageRequest.of(offset, limit, Sort.by(sortName)));
    }

    @GetMapping("/{id}")
    public RecipeDto getRecipe(@PathVariable int id){
        return recipeService.findRecipeById(id);
    }

    @PostMapping()
    public RecipeDto createRecipe(@Valid @RequestBody RecipeDto recipeDto) {
        return recipeService.createRecipe(recipeDto);
    }

    @PutMapping("/{id}")
    public RecipeDto changeRecipe(@PathVariable int id, @Valid @RequestBody RecipeDto recipeDto) {
        return recipeService.updateRecipe(id, recipeDto);
    }

    @DeleteMapping("/{id}")
    public void DeleteRecipe(@PathVariable int id) {
        recipeService.deleteRecipeById(id);
    }
}
