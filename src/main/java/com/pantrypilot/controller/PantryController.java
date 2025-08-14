package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.service.PantryIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pantry")
public class PantryController {

    @Autowired
    private PantryIngredientService service;

    @GetMapping
    public List<PantryIngredient> getAllIngredients() {
        return service.getAllIngredients();
    }

    @PostMapping
    public PantryIngredient addIngredient(@RequestBody PantryIngredient ingredient) {
        return service.saveIngredient(ingredient);
    }

    @DeleteMapping
    public void clearAllIngredients() {
        service.clearAllIngredients();
    }
}
