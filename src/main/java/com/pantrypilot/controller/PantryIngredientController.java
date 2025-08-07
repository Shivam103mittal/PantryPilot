package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.service.PantryIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
public class PantryIngredientController {

    @Autowired
    private PantryIngredientService pantryIngredientService;

    // Add a new ingredient
    @PostMapping
    public PantryIngredient addIngredient(@RequestBody PantryIngredient ingredient) {
        return pantryIngredientService.saveIngredient(ingredient);
    }

    // Get all ingredients
    @GetMapping
    public List<PantryIngredient> getAllIngredients() {
        return pantryIngredientService.getAllIngredients();
    }

    // Clear all ingredients
    @DeleteMapping
    public void clearIngredients() {
        pantryIngredientService.clearAllIngredients();
    }

    // Add multiple ingredients at once
    @PostMapping("/batch")
    public List<PantryIngredient> addIngredientsBatch(@RequestBody List<PantryIngredient> ingredients) {
        return pantryIngredientService.saveAllIngredients(ingredients);
    }

}
