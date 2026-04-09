package com.example.menuservice.dto;

import lombok.Data;

@Data
public class OllamaDto {
    private String model;
    private String response;
    private boolean done;
}