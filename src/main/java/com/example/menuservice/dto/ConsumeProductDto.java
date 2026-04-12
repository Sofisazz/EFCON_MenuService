package com.example.menuservice.dto;

import com.example.menuservice.enums.Measure;
import lombok.Data;

@Data
public class ConsumeProductDto {
    private String productName;
    private double amount;
    private Measure unit;
    private Integer userId;
    private Integer productId;
}