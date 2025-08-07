package com.pantrypilot.repository;

import com.pantrypilot.model.Recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT DISTINCT r FROM Recipe r JOIN r.ingredients i WHERE i.ingredientName IN :ingredientNames")
    List<Recipe> findByIngredientNamesIn(@Param("ingredientNames") List<String> ingredientNames);

}
