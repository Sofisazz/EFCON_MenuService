package com.example.menuservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserRestrictionsDto {
    private int id;
    private List<String> allergies;
    private List<String> unfavoriteFoods;
    private List<String> foodTriggers;
}
