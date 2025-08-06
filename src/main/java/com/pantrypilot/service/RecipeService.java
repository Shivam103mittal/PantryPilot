package com.pantrypilot.service;

import com.pantrypilot.model.Recipe;

import java.util.List;

public interface RecipeService {
    Recipe saveRecipe(Recipe recipe);
    List<Recipe> getAllRecipes();
    void clearAllRecipes();
}
