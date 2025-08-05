package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;

import java.util.List;

public interface PantryIngredientService {
    PantryIngredient saveIngredient(PantryIngredient ingredient);
    List<PantryIngredient> getAllIngredients();
    void clearAllIngredients();
}
