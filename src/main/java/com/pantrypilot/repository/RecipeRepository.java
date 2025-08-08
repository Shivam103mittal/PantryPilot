package com.pantrypilot.repository;

import com.pantrypilot.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT r FROM Recipe r JOIN r.ingredients i " +
           "WHERE LOWER(i.ingredientName) IN :ingredientNames " +
           "GROUP BY r.id " +
           "HAVING COUNT(DISTINCT LOWER(i.ingredientName)) <= :maxMissingCount")
    List<Recipe> findByIngredientNamesAndMaxMissing(Set<String> ingredientNames, int maxMissingCount);
}
