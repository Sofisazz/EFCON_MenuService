package com.example.menuservice.repository;

import com.example.menuservice.entity.EatingPlan;
import com.example.menuservice.enums.EatingType;
import com.example.menuservice.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EatingPlanRepository extends JpaRepository<EatingPlan, Integer> {

    boolean existsByDateAndType(LocalDate date, EatingType type);
    boolean existsByDateAndTypeAndIdNot(LocalDate date, EatingType type, int id);

    Page<EatingPlan> findByUserId(int userId, PageRequest pageable);
    Optional<EatingPlan> findByIdAndUserId(int id, int userId);
    boolean existsByIdAndUserId(int id, int userId);

    boolean existsByDateAndTypeAndUserIdAndIdNot(LocalDate date, EatingType type, int userId, int id);

    boolean existsByDateAndTypeAndUserIdAndStatusNot(LocalDate date, EatingType type, int userId, Status status);

    long countByDateAndTypeAndUserIdAndStatusNot(LocalDate date, EatingType type, int userId, Status status);

    boolean existsByDateAndTypeAndUserIdAndIdNotAndStatusNot(LocalDate date, EatingType type, int userId, int id, Status status);
}
