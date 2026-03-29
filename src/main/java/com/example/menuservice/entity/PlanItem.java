package com.example.menuservice.entity;

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
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ColumnDefault("1")
    @Column(nullable = false)
    private int portions;

    public PlanItem(){}

    public PlanItem(Recipe recipe, int portions) {
        this.recipe = recipe;
        this.portions = portions;
    }
}