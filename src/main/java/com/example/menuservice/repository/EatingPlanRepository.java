package com.example.menuservice.repository;

import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.enums.EatingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface EatingPlanRepository extends JpaRepository<EatingPlan, Integer> {

    boolean existsByDateAndType(LocalDate date, EatingType type);
    boolean existsByDateAndTypeAndIdNot(LocalDate date, EatingType type, int id);
}
