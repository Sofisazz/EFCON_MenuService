package com.example.menuservice.service.serviceImpl;

import com.example.menuservice.dto.RecipeDto;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.Recipe;
import com.example.menuservice.exceptions.ExistsException;
import com.example.menuservice.exceptions.MissingException;
import com.example.menuservice.exceptions.UpdateException;
import com.example.menuservice.repository.RecipeRepository;
import com.example.menuservice.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeMapper recipeMapper;

    @Override
    public Page<RecipeDto> findAllRecipes(Pageable pageable) {
        return recipeRepository.findAll(pageable).map(recipeMapper::toDto);
    }

    @Override
    public RecipeDto findRecipeById(int id) {
        return recipeRepository.findById(id).map(recipeMapper::toDto)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));
    }

    @Transactional
    @Override
    public RecipeDto createRecipe(RecipeDto recipeDto) {
        if(recipeRepository.existsByName(recipeDto.getName())) {
            throw new ExistsException("Рецепт с названием '" + recipeDto.getName() + "' уже существует");
        }

        return recipeMapper.toDto(recipeRepository.save(recipeMapper.toEntity(recipeDto))) ;
    }

    @Transactional
    @Override
    public RecipeDto updateRecipe(int id, RecipeDto recipeDto) {
        Recipe existingRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));

        existsRecipeByName(existingRecipe.getName(), id);
        recipeMapper.updateFromDto(recipeDto, existingRecipe);
        recipeRepository.flush();

        return recipeMapper.toDto(existingRecipe);
    }

    @Transactional
    @Override
    public void deleteRecipeById(int id) {
        Recipe receivedRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new MissingException("Рецепт с id '" + id + "' не существует"));

        List<EatingPlan> eatingPlans = receivedRecipe.getEatingPlan();
        List<String> deletePlans = new ArrayList<>();

        for (EatingPlan plan : eatingPlans) {
            if (plan.getRecipe().equals(receivedRecipe)) {
                deletePlans.add("id: " + plan.getId() + ": " + plan.getType());
            }
        }

        if (!deletePlans.isEmpty()) {
            throw new ExistsException("Для удаления рецепта удалите план питания или назначьте другой рецепт " + deletePlans);
        }

        recipeRepository.deleteById(id);
    }


    private void existsRecipeByName(String name, int id) {
        if(recipeRepository.existsByName(name)) {
            Recipe receivedRecipe = recipeRepository.findByName(name);

            if(receivedRecipe.getId() != id) {
                throw new UpdateException("Рецепт с названием '" + name + "' уже существует");
            }
        }
    }

}
