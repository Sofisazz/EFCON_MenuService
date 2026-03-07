package com.example.menuservice.entity;

import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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

    @NotNull(message = "Количество человек обязательно")
    @Column(nullable = false)
    private Integer numberOfPeople;

    @NotNull(message = "Статус обязателен")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @NotNull(message = "Рецепт обязатателен")
    @ManyToOne
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;
}
