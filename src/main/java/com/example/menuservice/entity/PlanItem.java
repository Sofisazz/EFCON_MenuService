package com.example.menuservice.entity;

import com.example.menuservice.enums.Measure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "plan_items")
@Getter
@Setter
public class PlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private EatingPlan eatingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    private Integer productId;

    @Enumerated(EnumType.STRING)
    private Measure unit;

    @ColumnDefault("1")
    @Column(nullable = false)
    private int portions;

    public PlanItem(){}

    public PlanItem(Recipe recipe, int portions) {
        this.recipe = recipe;
        this.portions = portions;
    }

    public PlanItem(Integer productId, Measure unit, int amount) {
        this.productId = productId;
        this.unit = unit;
        this.portions = amount;
    }
}