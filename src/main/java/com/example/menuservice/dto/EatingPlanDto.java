package com.example.menuservice.dto;

import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Status;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EatingPlanDto {

    private int id;
    private LocalDate date;
    private EatingType type;
    private Status status;
    private List<PlanItemDto> items;
}
