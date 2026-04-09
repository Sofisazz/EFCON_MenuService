package com.example.menuservice.service.V1.serviceImpl;

import com.example.menuservice.dto.*;
import com.example.menuservice.dto.mapping.EatingPlanMapper;
import com.example.menuservice.dto.mapping.RecipeMapper;
import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.entity.Recipe;
import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Status;
import com.example.menuservice.exceptions.ExistsException;
import com.example.menuservice.exceptions.MissingException;
import com.example.menuservice.repository.EatingPlanRepository;
import com.example.menuservice.service.V1.EatingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Deprecated
@RequiredArgsConstructor
@Service
public class EatingPlanServiceImpl implements EatingPlanService {

    private final EatingPlanRepository eatingPlanRepository;
    //private final RecipeRepository recipeRepository;

    private final EatingPlanMapper eatingPlanMapper;
    private final RecipeMapper recipeMapper;

    @Override
    public Page<EatingPlanDto> findAllEatingPlans(Pageable pageable) {
        return eatingPlanRepository.findAll(pageable).map(eatingPlanMapper::toDto);
    }

    @Override
    public EatingPlanDto findEatingPlanById(int id) {
        return eatingPlanRepository.findById(id).map(eatingPlanMapper::toDto)
                .orElseThrow(() -> new MissingException("Данного плана питания не существует"));
    }

    @Transactional
    @Override
    public EatingPlanDto createEatingPlan(EatingPlanDto eatingPlanDto) {
        if (!eatingPlanDto.getType().equals(EatingType.SNACK) && eatingPlanRepository.existsByDateAndType(eatingPlanDto.getDate(), eatingPlanDto.getType())) {
            throw new ExistsException("План питания на " + eatingPlanDto.getDate() + ": " + eatingPlanDto.getType() + " существует");
        }

        //Recipe receivedRecipe = findRecipeForPlan(eatingPlanDto);
        EatingPlan receivedEatingPlan = eatingPlanMapper.toEntity(eatingPlanDto);
        //receivedEatingPlan.setRecipe(receivedRecipe);

        return eatingPlanMapper.toDto(eatingPlanRepository.save(receivedEatingPlan));
    }

    @Transactional
    @Override
    public EatingPlanDto updateEatingPlan(int id, EatingPlanDto eatingPlanDto) {
        EatingPlan existingEatingPlan = eatingPlanRepository.findById(id)
                .orElseThrow(() -> new MissingException("План питания с id '" + id + "' не существует"));

        if (!eatingPlanDto.getType().equals(EatingType.SNACK) && eatingPlanRepository.existsByDateAndTypeAndIdNot(eatingPlanDto.getDate(), eatingPlanDto.getType(), id)) {
            throw new ExistsException("План питания на " + eatingPlanDto.getDate() + ": " + eatingPlanDto.getType() + " существует");
        }

        //Recipe receivedRecipe = findRecipeForPlan(eatingPlanDto);
        eatingPlanMapper.updateFromDto(eatingPlanDto, existingEatingPlan);
        //existingEatingPlan.setRecipe(receivedRecipe);

        return eatingPlanMapper.toDto(existingEatingPlan);
    }

    @Transactional
    @Override
    public void deleteEatingPlanById(int id) {
        if(!eatingPlanRepository.existsById(id)) {
            throw new MissingException("План питания с id '" + id + "' не существует");
        }

        eatingPlanRepository.deleteById(id);
    }

    @Transactional
    @Override
    public EatingPlanDto updateStatusEatingPlan(int id, Status status) {
        EatingPlan receivedEatingPlan = eatingPlanRepository.findById(id)
                .orElseThrow(() ->  new MissingException("План питания с id '" + id + "' не существует"));

        receivedEatingPlan.setStatus(status);
        eatingPlanRepository.save(receivedEatingPlan);

        return  eatingPlanMapper.toDto(receivedEatingPlan);
    }

    @Override
    public RecipeDto findRecipeForPlan(int id) {
        //EatingPlan receivedPlan = eatingPlanRepository.findById(id)
         //       .orElseThrow(() -> new MissingException("Плана питания с id '" + id + "' не существует"));

        //Recipe recipe = receivedPlan.getRecipe();
        Recipe recipe = new Recipe();
        return recipeMapper.toDto(recipe);
    }

   /* private Recipe findRecipeForPlan(EatingPlanDto eatingPlanDto) {

        Integer recipeId = Optional.ofNullable(eatingPlanDto.getRecipeId())
                .orElseThrow(() -> new MissingException("Поле 'recipeId' не передано"));

        return recipeRepository.findById(recipeId)
               .orElseThrow(() -> new MissingException("Рецепта с id '" + recipeId + "' не существует"));

        return new Recipe();
    }
*/



}
