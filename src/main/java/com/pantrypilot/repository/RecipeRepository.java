package com.pantrypilot.repository;

import com.pantrypilot.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.util.Set;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT DISTINCT r FROM Recipe r JOIN r.ingredients i " +
            "WHERE r.prepTime BETWEEN :minPrepTime AND :maxPrepTime " +
            "AND LOWER(i.ingredientName) IN :ingredientNames")
    List<Recipe> findByPrepTimeAndIngredients(
            @Param("minPrepTime") int minPrepTime,
            @Param("maxPrepTime") int maxPrepTime,
            @Param("ingredientNames") Set<String> ingredientNames);

    Optional<Recipe> findByTitle(String title);
}
