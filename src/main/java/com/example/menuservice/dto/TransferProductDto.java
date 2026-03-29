package com.example.menuservice.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class TransferProductDto {
    private int id;
    private String name;
    private String category;
    private String barcode;
    private List<ProductInstanceDto> instances = new ArrayList<>();
    private String brand;

    private double calories;
    private double proteins;
    private double fats;
    private double carbohydrates;
    private LocalDate expirationDate;

}
