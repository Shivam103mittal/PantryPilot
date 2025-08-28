package com.pantrypilot.controller;

import com.pantrypilot.dto.RecipeDTO;
import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.IngredientImageService;
import com.pantrypilot.service.RecipeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final IngredientImageService ingredientImageService;

    public RecipeController(RecipeService recipeService, IngredientImageService ingredientImageService) {
        this.recipeService = recipeService;
        this.ingredientImageService = ingredientImageService;
    }

    // Add a new recipe
    @PostMapping
    public Recipe addRecipe(@RequestBody Recipe recipe) {
        return recipeService.saveRecipe(recipe);
    }

    // Get all recipes
    @GetMapping
    public List<RecipeDTO> getAllRecipes() {
        return recipeService.getAllRecipes().stream()
                .map(recipe -> new RecipeDTO(recipe, ingredientImageService))
                .collect(Collectors.toList());
    }

    // Clear all recipes
    @DeleteMapping
    public void clearAllRecipes() {
        recipeService.clearAllRecipes();
    }

    @DeleteMapping("/{id}")
    public void deleteRecipeById(@PathVariable Long id) {
        recipeService.deleteRecipeById(id);
    }

    // Get a recipe by ID
    @GetMapping("/{id}")
    public ResponseEntity<RecipeDTO> getRecipeById(@PathVariable Long id) {
        Recipe recipe = recipeService.getRecipeById(id);
        if (recipe == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new RecipeDTO(recipe, ingredientImageService));
    }

    @PostMapping("/filter")
    public List<Recipe> filterRecipes(
            @RequestParam int minPrepTime,
            @RequestParam int maxPrepTime,
            @RequestBody List<PantryIngredient> pantryIngredients) {
        return recipeService.getRecipesByPrepTimeAndIngredients(
                minPrepTime, maxPrepTime, pantryIngredients);
    }

}
