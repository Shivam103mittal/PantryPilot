package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PantryIngredientService {

    @Autowired
    private PantryIngredientRepository repository;

    public List<PantryIngredient> getAllIngredients() {
        return repository.findAll();
    }

    public PantryIngredient saveIngredient(PantryIngredient ingredient) {
        return repository.save(ingredient);
    }

    public void clearAllIngredients() {
        repository.deleteAll();
    }
}
