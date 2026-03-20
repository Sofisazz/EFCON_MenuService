package com.example.menuservice.entity;

import com.example.menuservice.enums.Measure;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recipe_ingredients")
@Getter
@Setter
public class RecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private int id;

    @NotNull(message = "Рецепт обязателен")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @NotNull(message = "Название ингредиента обязательно")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Количество ингредиента обязательно")
    @Column(nullable = false)
    private double quantity;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Единица измерения ингредиента обязательно")
    @Column(nullable = false)
    private Measure unit;
}
