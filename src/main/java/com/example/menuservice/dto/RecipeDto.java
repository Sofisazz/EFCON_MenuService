package com.example.menuservice.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class RecipeDto {

    private int id;
    private String name;
    private double caloriesFor100;
    private double proteins;
    private double fats;
    private double carbohydrates;

    private Set<String> ingredients;

    private Map<Integer, String> steps;
}
