package com.example.menuservice.dto;

import com.example.menuservice.enums.Measure;
import lombok.Data;

@Data
public class PlanItemDto {
    private int id;
    private Integer recipeId;
    private Integer productId;
    private Measure unit;
    private int portions;
}