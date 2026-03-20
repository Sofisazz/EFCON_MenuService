package com.example.menuservice.dto;

import com.example.menuservice.enums.Measure;
import lombok.Data;

@Data
public class ProductStatusDto {
    private String name;
    private boolean available;
    private double requiredAmount;
    private double availableAmount;
    private double toBuyAmount;
    private Measure unit;
}
