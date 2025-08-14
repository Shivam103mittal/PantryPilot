package com.pantrypilot.repository;

import com.pantrypilot.model.PantryIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PantryIngredientRepository extends JpaRepository<PantryIngredient, Long> {
}
