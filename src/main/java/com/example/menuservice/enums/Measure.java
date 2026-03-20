package com.example.menuservice.enums;

import lombok.Getter;

@Getter
public enum Measure {
    ML("Милиллитры"),
    L("Литры"),
    KG("Килограммы"),
    G("Граммы"),
    PCS("Штуки");

    private final String description;
    Measure(String description) {
        this.description = description;
    }
}
