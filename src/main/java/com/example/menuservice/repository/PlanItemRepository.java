package com.example.menuservice.repository;

import com.example.menuservice.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanItemRepository extends JpaRepository<PlanItem, Integer> {
    List<PlanItem> findByRecipeId(int recipeId);
}
