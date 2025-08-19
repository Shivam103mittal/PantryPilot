package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.util.UnitConverter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeMatcherService {

    private final PantryIngredientRepository pantryIngredientRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;

    public RecipeMatcherService(PantryIngredientRepository pantryIngredientRepository,
                                RecipeRepository recipeRepository,RecipeService recipeService) {
        this.pantryIngredientRepository = pantryIngredientRepository;
        this.recipeRepository = recipeRepository;
        this.recipeService = recipeService;
    }

    public List<Recipe> matchRecipes(List<PantryIngredient> providedPantry) {
        List<PantryIngredient> pantryIngredients = (providedPantry != null && !providedPantry.isEmpty())
                ? providedPantry
                : pantryIngredientRepository.findAll();

        // Normalize pantry map
        Map<String, PantryIngredient> pantryMap = pantryIngredients.stream()
                .collect(Collectors.toMap(
                        pi -> normalizeName(pi.getIngredientName()),
                        pi -> pi,
                        (pi1, pi2) -> pi1
                ));

        System.out.println("=== Pantry Map ===");
        pantryMap.forEach((name, ing) ->
                System.out.println(name + " -> " + ing.getQuantity() + " " + ing.getUnit())
        );

        List<Recipe> allRecipes = recipeRepository.findAll();
        List<Recipe> matchedRecipes = new ArrayList<>();

        for (Recipe recipe : allRecipes) {
            if (canMakeRecipe(recipe, pantryMap)) {
                matchedRecipes.add(recipe);
            }
        }

        return matchedRecipes;
    }

    private boolean canMakeRecipe(Recipe recipe, Map<String, PantryIngredient> pantryMap) {
        System.out.println("\nChecking recipe: " + recipe.getTitle());

        for (RecipeIngredient req : recipe.getIngredients()) {
            String reqName = normalizeName(req.getIngredientName());
            String reqUnit = normalizeUnit(req.getUnit());

            PantryIngredient pantry = findPantryMatch(reqName, pantryMap);
            if (pantry == null) {
                System.out.println("❌ Missing ingredient: " + reqName);
                return false;
            }

            double pantryQty = UnitConverter.convert(pantry.getQuantity(), pantry.getUnit(), reqUnit);
            System.out.printf(
                    "Ingredient: %s | Need: %.2f %s | Have: %.2f %s%n",
                    reqName, req.getQuantity(), reqUnit,
                    pantryQty, reqUnit
            );

            if (pantryQty < req.getQuantity()) {
                System.out.println("❌ Not enough: " + reqName);
                return false;
            }
        }

        System.out.println("✅ Can make: " + recipe.getTitle());
        return true;
    }

    // Normalize to lowercase, trimmed
    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.trim().toLowerCase();
    }

    // Slightly tolerant matching (e.g., "tomato" matches "tomatoes")
    private PantryIngredient findPantryMatch(String reqName, Map<String, PantryIngredient> pantryMap) {
        if (pantryMap.containsKey(reqName)) {
            return pantryMap.get(reqName);
        }
        // Try plural/singular stripping
        if (reqName.endsWith("es") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 2))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 2));
        }
        if (reqName.endsWith("s") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 1))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 1));
        }
        // Try contains match
        for (String pantryKey : pantryMap.keySet()) {
            if (pantryKey.contains(reqName) || reqName.contains(pantryKey)) {
                return pantryMap.get(pantryKey);
            }
        }
        return null;
    }

    public List<Recipe> matchRecipesWithPrepTime(
        List<PantryIngredient> pantryIngredients, int minPrepTime, int maxPrepTime) {
    return recipeService.getRecipesByPrepTimeAndIngredients(minPrepTime, maxPrepTime, pantryIngredients);
}

}
