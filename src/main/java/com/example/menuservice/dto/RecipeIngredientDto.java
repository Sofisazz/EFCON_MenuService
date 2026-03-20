package com.example.menuservice.dto;

import com.example.menuservice.enums.Measure;
import lombok.Data;

@Data
public class RecipeIngredientDto {
    private String name;
    private double quantity;
    private Measure unit;
}
