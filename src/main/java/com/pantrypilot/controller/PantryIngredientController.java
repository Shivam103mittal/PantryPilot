package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
public class PantryIngredientController {

    @Autowired
    private PantryIngredientRepository pantryIngredientRepository;

    // Add a new ingredient
    @PostMapping
    public PantryIngredient addIngredient(@RequestBody PantryIngredient ingredient) {
        return pantryIngredientRepository.save(ingredient);
    }

    // Get all ingredients
    @GetMapping
    public List<PantryIngredient> getAllIngredients() {
        return pantryIngredientRepository.findAll();
    }

    @GetMapping("/test")
    public String test() {
        return "PantryPilot is working!";
}

}
