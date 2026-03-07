package com.example.menuservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "recipes")
@Getter
@Setter
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private int id;

    @NotBlank(message = "Название рецепта обязательно")
    @Size(min = 2, max = 200, message = "Количество символов от 2 до 200")
    @Column(nullable = false, length = 200, unique = true)
    private String name;

    @NotNull(message = "Рецепт должен содержать ингредиенты")
    @ElementCollection
    @CollectionTable(name="recipe_ingredients", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "ingredient", nullable = false)
    private Set<String> ingredients;

    @ElementCollection
    @CollectionTable(name="recipe_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @MapKeyColumn(name = "number_step")
    @Column(name = "description")
    private Map<Integer, String> steps;

    @OneToMany(mappedBy = "recipe")
    private List<EatingPlan> eatingPlan;

    private double caloriesFor100;

    @ColumnDefault("0.0")
    private double proteins;

    @ColumnDefault("0.0")
    private double fats;

    @ColumnDefault("0.0")
    private double carbohydrates;

    @PrePersist
    @PreUpdate
    public void calculateCalories() {
        this.caloriesFor100 = (this.proteins * 4.0) + (this.fats * 9.0) + (this.carbohydrates * 4.0);
    }
}
