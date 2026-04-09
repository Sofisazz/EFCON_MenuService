package com.example.menuservice.enums;

import lombok.Getter;

@Getter
public enum Measure {
    ML("Милиллитры"),
    L("Литры"),
    KG("Килограммы"),
    G("Граммы"),
    PCS("Штуки"),
    TSP("Чайные ложки"),
    TBSP("Столовые ложки"),
    PACKET("Пакетик"),
    PINCH("Щепотки"),
    CUP("Чашки");

    private final String description;
    Measure(String description) {
        this.description = description;
    }

    public static Measure getMeasure(String lowerMeasure) {

        Measure unit = Measure.PCS;

        if (lowerMeasure.contains("teaspoon") || lowerMeasure.contains("tsp")) {
            unit = Measure.TSP;
        } else if (lowerMeasure.contains("tablespoon") || lowerMeasure.contains("tbsp")) {
            unit = Measure.TBSP;
        } else if (lowerMeasure.contains("cup")) {
            unit = Measure.CUP;
        } else if (lowerMeasure.contains("pinch")) {
            unit = Measure.PINCH;
        } else if (lowerMeasure.contains("packet") || lowerMeasure.contains("package")) {
            unit = Measure.PACKET;
        } else if (lowerMeasure.contains("g") || lowerMeasure.contains("gram")) {
            unit = Measure.G;
        } else if (lowerMeasure.contains("kg")) {
            unit = Measure.KG;
        } else if (lowerMeasure.contains("ml")) {
            unit = Measure.ML;
        } else if (lowerMeasure.contains("l") && !lowerMeasure.contains("pl")) {
            unit = Measure.L;
        }

        return unit;
    }
}
