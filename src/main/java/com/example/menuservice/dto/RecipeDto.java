package com.example.menuservice.dto;

import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
public class RecipeDto {

    private int id;
    private String name;
    private double caloriesFor100;
    private double proteins;
    private double fats;
    private double carbohydrates;
    private int serving;

    private List<RecipeIngredientDto> ingredients;

    private Map<Integer, String> steps;

    private Integer ownerId;
}
