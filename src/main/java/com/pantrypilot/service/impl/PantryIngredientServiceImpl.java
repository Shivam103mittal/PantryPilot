package com.pantrypilot.service.impl;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import com.pantrypilot.service.PantryIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PantryIngredientServiceImpl implements PantryIngredientService {

    private final PantryIngredientRepository pantryIngredientRepository;

    @Autowired
    public PantryIngredientServiceImpl(PantryIngredientRepository pantryIngredientRepository) {
        this.pantryIngredientRepository = pantryIngredientRepository;
    }

    @Override
    public PantryIngredient saveIngredient(PantryIngredient ingredient) {
        return pantryIngredientRepository.save(ingredient);
    }

    @Override
    public List<PantryIngredient> getAllIngredients() {
        return pantryIngredientRepository.findAll();
    }

    @Override
    public void clearAllIngredients() {
        pantryIngredientRepository.deleteAll();
    }

    @Override
    public List<PantryIngredient> saveAllIngredients(List<PantryIngredient> ingredients) {
    return pantryIngredientRepository.saveAll(ingredients);
}

}
