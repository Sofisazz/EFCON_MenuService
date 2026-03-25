package com.example.menuservice.entity;

import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eating_plan")
@Getter
@Setter
public class EatingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private int id;

    @NotNull(message = "Дата обязательна")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull(message = "Тип приема пищи обязателен (завтрак, обед и тд.)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EatingType type;


    @NotNull(message = "Статус обязателен")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @OneToMany(mappedBy = "eatingPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanItem> planItems = new ArrayList<>();

    @NotNull(message = "Id пользователя обязателен")
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    public void addPlanItem(Recipe recipe, int portions) {
        PlanItem item = new PlanItem(recipe, portions);
        item.setEatingPlan(this);
        this.planItems.add(item);
    }

}
