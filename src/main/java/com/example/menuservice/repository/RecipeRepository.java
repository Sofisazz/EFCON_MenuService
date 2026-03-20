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

    Recipe findByNameAndOwnerId(String name, Integer userId);

    boolean existsByNameAndOwnerIdIsNotNull(String name);

    Optional<Recipe> findByIdAndOwnerIdIsNullOrOwnerId(int id, int userId);

    List<Recipe> findByOwnerIdIsNullOrOwnerId(int userId);
}

