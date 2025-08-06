package com.pantrypilot.service.impl;

import com.pantrypilot.model.Recipe;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.service.RecipeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeServiceImpl implements RecipeService {

    @Autowired
    private RecipeRepository recipeRepository;

    @Override
    public Recipe saveRecipe(Recipe recipe) {
        return recipeRepository.save(recipe);
    }

    @Override
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    @Override
    public void clearAllRecipes() {
        recipeRepository.deleteAll();
    }
}
