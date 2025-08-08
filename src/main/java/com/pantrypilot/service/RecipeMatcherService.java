package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.util.UnitConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeMatcherService {

    private final RecipeRepository recipeRepository;

    /**
     * Match recipes based on the given pantry ingredients.
     * Returns all matched recipes (no pagination here).
     */
    public List<Recipe> matchRecipes(List<PantryIngredient> pantryIngredients) {
        if (pantryIngredients == null || pantryIngredients.isEmpty()) {
            System.out.println("Pantry ingredients empty or null");
            return Collections.emptyList();
        }

        Set<String> ingredientNames = pantryIngredients.stream()
                .map(pi -> pi.getIngredientName().toLowerCase())
                .collect(Collectors.toSet());

        System.out.println("Pantry ingredient names: " + ingredientNames);

        List<Recipe> candidateRecipes = recipeRepository.findByIngredientNamesAndMaxMissing(
                ingredientNames,
                pantryIngredients.size()
        );

        System.out.println("Candidate recipes found: " + candidateRecipes.size());

        List<Recipe> matchedRecipes = candidateRecipes.stream()
                .filter(recipe -> recipeMatchesPantry(recipe, pantryIngredients))
                .collect(Collectors.toList());

        System.out.println("Matched recipes after filtering: " + matchedRecipes.size());

        return matchedRecipes;
    }

    private boolean recipeMatchesPantry(Recipe recipe, List<PantryIngredient> pantryIngredients) {
        for (var recipeIngredient : recipe.getIngredients()) {
            PantryIngredient matchingPantryIngredient = pantryIngredients.stream()
                    .filter(pi -> pi.getIngredientName().equalsIgnoreCase(recipeIngredient.getIngredientName()))
                    .findFirst()
                    .orElse(null);

            if (matchingPantryIngredient == null) {
                return false;
            }

            double pantryQty = UnitConverter.convert(
                    matchingPantryIngredient.getQuantity(),
                    matchingPantryIngredient.getUnit(),
                    recipeIngredient.getUnit()
            );

            if (pantryQty < recipeIngredient.getQuantity()) {
                return false;
            }
        }
        return true;
    }
}
