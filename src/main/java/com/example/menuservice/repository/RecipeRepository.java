package com.example.menuservice.repository;

import com.example.menuservice.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    boolean existsByName(String name);
    Recipe findByName(String name);

    boolean existsByNameAndOwnerIdIsNull(String name);
    boolean existsByNameAndOwnerId(String name, Integer ownerId);

    List<Recipe> findByOwnerIdOrOwnerIdIsNull(int userId);

    Recipe findByNameAndOwnerIdIsNull(String name);

    Optional<Recipe> findByNameAndOwnerId(String name, Integer userId);

    boolean existsByNameAndOwnerIdIsNotNull(String name);

    List<Recipe> findByOwnerIdIsNullOrOwnerId(int userId);

    List<Recipe> findByIngredientsNameIgnoreCaseAndOwnerId(String name, int userId);

    List<Recipe> findByNameContainingIgnoreCaseAndOwnerId(String name, int userId);

    List<Recipe> findByNameContainingIgnoreCaseAndOwnerIdIsNull(String name);
}

