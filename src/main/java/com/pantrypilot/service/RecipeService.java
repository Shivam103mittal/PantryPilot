package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RecipeService {
    Recipe saveRecipe(Recipe recipe);

    List<Recipe> getAllRecipes();

    void clearAllRecipes();

    Recipe getRecipeById(Long id);

    List<Recipe> getRecipesByPrepTimeAndIngredients(
            int minPrepTime,
            int maxPrepTime,
            List<PantryIngredient> pantryIngredients
    );

    List<Recipe> saveAll(List<Recipe> recipes);
    Recipe saveAIRecipe(Map<String, Object> aiRecipe);
    

}
