package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.PantryIngredientService;
import com.pantrypilot.service.RecipeMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matching-recipes")
@RequiredArgsConstructor
public class RecipeMatcherController {

    private final RecipeMatcherService recipeMatcherService;
    private final PantryIngredientService pantryIngredientService;

    // Match recipes using pantry ingredients provided in request body
    @PostMapping
    public List<Recipe> getMatchingRecipes(@RequestBody List<PantryIngredient> pantryIngredients) {
        return recipeMatcherService.findMatchingRecipes(pantryIngredients);
    }

    
    @GetMapping
    public List<Recipe> getMatchingRecipesFromStoredPantry() {
        List<PantryIngredient> storedPantry = pantryIngredientService.getAllIngredients();
        List<Recipe> matchingRecipes = recipeMatcherService.findMatchingRecipes(storedPantry);

        
        pantryIngredientService.clearAllIngredients();

        return matchingRecipes;
    }

}
