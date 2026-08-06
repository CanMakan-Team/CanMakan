package com.canmakan.backend.knowledgebase.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientEntityRepository extends JpaRepository<IngredientEntity, Long> {

    Optional<IngredientEntity> findByIngredientNameIgnoreCase(String ingredientName);

    List<IngredientEntity> findByIngredientNameContainingIgnoreCase(String ingredientName);

    List<IngredientEntity> findByIsChemicalAliasTrue();
}
