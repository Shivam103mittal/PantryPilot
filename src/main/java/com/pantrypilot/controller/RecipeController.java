package com.pantrypilot.controller;

import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.RecipeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @Autowired
    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // Add a new recipe
    @PostMapping
    public Recipe addRecipe(@RequestBody Recipe recipe) {
        return recipeService.saveRecipe(recipe);
    }

    // Get all recipes
    @GetMapping
    public List<Recipe> getAllRecipes() {
        return recipeService.getAllRecipes();
    }

    // Clear all recipes
    @DeleteMapping
    public void clearAllRecipes() {
        recipeService.clearAllRecipes();
    }
}
