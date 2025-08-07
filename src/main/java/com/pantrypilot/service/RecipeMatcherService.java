package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.util.UnitConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeMatcherService {

    private final RecipeRepository recipeRepository;
    private final UnitConverter unitConverter;

    @Autowired
    public RecipeMatcherService(RecipeRepository recipeRepository, UnitConverter unitConverter) {
        this.recipeRepository = recipeRepository;
        this.unitConverter = unitConverter;
    }

    public List<Recipe> findMatchingRecipes(List<PantryIngredient> pantryIngredients) {

        Map<String, PantryIngredient> pantryMap = pantryIngredients.stream()
                .collect(Collectors.toMap(
                        p -> p.getIngredientName().toLowerCase(),
                        p -> p
                ));

    
        Set<String> pantryNames = pantryMap.keySet();

    
        List<Recipe> candidateRecipes = recipeRepository.findByIngredientNamesIn(new ArrayList<>(pantryNames));

        List<Recipe> matchedRecipes = new ArrayList<>();

        
        for (Recipe recipe : candidateRecipes) {
            boolean allIngredientsMatch = true;

            for (RecipeIngredient recipeIng : recipe.getIngredients()) {
                PantryIngredient pantryIng = pantryMap.get(recipeIng.getIngredientName().toLowerCase());

                if (pantryIng == null) {
                    allIngredientsMatch = false;
                    break;
                }

            
                double pantryQtyInRecipeUnit = unitConverter.convert(
                        pantryIng.getQuantity(),
                        pantryIng.getUnit(),
                        recipeIng.getUnit()
                );

                if (pantryQtyInRecipeUnit < recipeIng.getQuantity()) {
                    allIngredientsMatch = false;
                    break;
                }
            }

            if (allIngredientsMatch) {
                matchedRecipes.add(recipe);
            }
        }

        return matchedRecipes;
    }
}
