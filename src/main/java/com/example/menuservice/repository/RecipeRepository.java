package com.example.menuservice.repository;

import com.example.menuservice.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    boolean existsByName(String name);
    Recipe findByName(String name);
}
