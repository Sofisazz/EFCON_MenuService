package com.example.menuservice.dto;

import com.example.menuservice.enums.Measure;
import lombok.Data;

@Data
public class ProductRequirementDto {
    private String name;
    private double requiredAmount;
    private Measure unit;
}
