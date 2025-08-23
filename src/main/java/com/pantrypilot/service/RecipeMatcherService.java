package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.util.UnitConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeMatcherService {

    private final PantryIngredientRepository pantryIngredientRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final RecipeAIService recipeAIService;
    private final RecipeCacheService recipeCacheService;

    /**
     * Match recipes with prep time filter.
     * Falls back to AI if DB has fewer than batchSize recipes.
     * Adds results to cache and returns a token for pagination.
     */
    public String matchRecipesWithCache(
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime,
            int batchSize
    ) throws Exception {

        if (pantryIngredients == null) pantryIngredients = Collections.emptyList();

        // Step 1: fetch DB recipes
        List<Recipe> matchedRecipes = recipeService.getRecipesByPrepTimeAndIngredients(
                minPrepTime, maxPrepTime, pantryIngredients
        );

        // Step 2: Determine if AI fallback is needed
        if (matchedRecipes.size() < batchSize) {
            int missing = batchSize - matchedRecipes.size();

            List<Map<String, Object>> ingredientsForAI = pantryIngredients.stream()
                    .map(pi -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("ingredientName", pi.getIngredientName());
                        m.put("quantity", pi.getQuantity());
                        m.put("unit", pi.getUnit());
                        return m;
                    })
                    .collect(Collectors.toList());

            List<Recipe> aiRecipes = recipeAIService.generateRecipes(
                    ingredientsForAI,
                    minPrepTime,
                    maxPrepTime,
                    matchedRecipes.stream()
                            .map(Recipe::getTitle)
                            .filter(Objects::nonNull)
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet()),
                    missing
            );

            matchedRecipes.addAll(aiRecipes);
        }

        // Step 3: Add combined recipes + pantry to cache
        return recipeCacheService.addMatchedRecipes(matchedRecipes, pantryIngredients);
    }

    /**
     * Returns next batch of recipes for a token.
     */
    public List<Recipe> getNextBatch(String token, int batchSize) {
        return recipeCacheService.getNextRecipes(token, batchSize);
    }

    /**
     * Existing pantry-based matching logic (optional).
     */
    public List<Recipe> matchRecipes(List<PantryIngredient> providedPantry) {
        List<PantryIngredient> pantryIngredients = (providedPantry != null && !providedPantry.isEmpty())
                ? providedPantry
                : pantryIngredientRepository.findAll();

        Map<String, PantryIngredient> pantryMap = pantryIngredients.stream()
                .filter(pi -> pi.getIngredientName() != null)
                .collect(Collectors.toMap(
                        pi -> normalizeName(pi.getIngredientName()),
                        pi -> pi,
                        (pi1, pi2) -> pi1
                ));

        List<Recipe> allRecipes = recipeRepository.findAll();
        List<Recipe> matchedRecipes = new ArrayList<>();

        for (Recipe recipe : allRecipes) {
            if (canMakeRecipe(recipe, pantryMap)) {
                matchedRecipes.add(recipe);
            }
        }

        return matchedRecipes;
    }

    // --- Helper methods ---

    private boolean canMakeRecipe(Recipe recipe, Map<String, PantryIngredient> pantryMap) {
        if (recipe.getIngredients() == null) return false;

        for (RecipeIngredient req : recipe.getIngredients()) {
            if (req.getIngredientName() == null) return false;

            String reqName = normalizeName(req.getIngredientName());
            String reqUnit = normalizeUnit(req.getUnit());

            PantryIngredient pantry = findPantryMatch(reqName, pantryMap);
            if (pantry == null) return false;

            double pantryQty = UnitConverter.convert(pantry.getQuantity(), pantry.getUnit(), reqUnit);
            if (pantryQty < req.getQuantity()) return false;
        }
        return true;
    }

    private String normalizeName(String name) {
        return (name == null) ? "" : name.trim().toLowerCase();
    }

    private String normalizeUnit(String unit) {
        return (unit == null) ? "" : unit.trim().toLowerCase();
    }

    private PantryIngredient findPantryMatch(String reqName, Map<String, PantryIngredient> pantryMap) {
        if (pantryMap.containsKey(reqName)) return pantryMap.get(reqName);

        // singular/plural fallback
        if (reqName.endsWith("es") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 2))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 2));
        }
        if (reqName.endsWith("s") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 1))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 1));
        }

        // substring fuzzy match
        for (String pantryKey : pantryMap.keySet()) {
            if (pantryKey.contains(reqName) || reqName.contains(pantryKey)) {
                return pantryMap.get(pantryKey);
            }
        }

        return null;
    }
}
