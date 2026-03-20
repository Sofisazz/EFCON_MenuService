package com.example.menuservice.map;

import com.example.menuservice.exceptions.MissingException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AllergenMap {
    private final Map<String, List<String>> allergies = new HashMap<>();

    AllergenMap() {
        allergies.put("молочные продукты", List.of("молоко", "сыр", "творог", "сливки", "йогурт", "кефир", "масло сливочное", "лактоза", "казеин"));
        allergies.put("глютен", List.of("пшеница", "рожь", "ячмень", "овес", "мука", "хлеб", "булка", "клейковина", "манка", "булгур", "кускус"));
        allergies.put("орехи", List.of("орех", "миндаль", "фундук", "грецкий", "кешью", "арахис", "фисташки", "бразильский орех"));
        allergies.put("яйца", List.of("яйцо", "яичный порошок", "альбумин", "желток", "белок"));
        allergies.put("рыба", List.of("рыба", "лосось", "тунец", "треска", "икра", "морепродукты"));
        allergies.put("соя", List.of("соя", "тофу", "соевый соус", "текстурат"));
        allergies.put("морепродукты", List.of("креветки", "краб", "омар", "мидии", "устрицы", "кальмар"));
    }

    public boolean containsAllergen(String ingredientName, String allergy) {
        Optional.ofNullable(ingredientName)
                .orElseThrow(() -> new MissingException("Название ингредиента не передано"));

        Optional.ofNullable(allergy)
                .orElseThrow(() -> new MissingException("Имя аллергена не передано"));

        String ingredient = ingredientName.toLowerCase();

        List<String> keywords = Optional.ofNullable(allergies.get(allergy.toLowerCase()))
                .orElse(List.of());

        return keywords.stream().anyMatch(ingredient::contains);
    }
}
