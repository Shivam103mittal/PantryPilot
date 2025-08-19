package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.RecipeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    // Get a recipe by ID
    @GetMapping("/{id}")
    public ResponseEntity<Recipe> getRecipeById(@PathVariable Long id) {
        Recipe recipe = recipeService.getRecipeById(id);
        if (recipe == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(recipe);
    }

    @PostMapping("/filter")
    public List<Recipe> filterRecipes(
            @RequestParam int minPrepTime,
            @RequestParam int maxPrepTime,
            @RequestBody List<PantryIngredient> pantryIngredients
    ) {
        return recipeService.getRecipesByPrepTimeAndIngredients(
                minPrepTime, maxPrepTime, pantryIngredients
        );
    }

}
