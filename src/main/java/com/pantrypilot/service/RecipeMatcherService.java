package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.dto.RecipeDTO;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.PantryIngredientRepository;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.util.UnitConverter;

import org.springframework.transaction.annotation.Transactional;
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

    private static final int MAX_AI_RETRIES = 3; // safeguard against infinite loop

    /**
     * Match recipes with prep time filter.
     * Falls back to AI if DB has fewer than batchSize recipes.
     * Adds results to cache as DTOs and returns a token.
     */
    @Transactional
    public String matchRecipesWithCache(
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime,
            int batchSize) throws Exception {

        if (pantryIngredients == null)
            pantryIngredients = Collections.emptyList();

        List<Recipe> matchedRecipes = recipeService.getRecipesByPrepTimeAndIngredients(
                minPrepTime, maxPrepTime, pantryIngredients);

        if (matchedRecipes.size() < batchSize) {
            int missing = batchSize - matchedRecipes.size();

            List<Recipe> aiRecipes = fetchValidAIRecipes(
                    pantryIngredients,
                    minPrepTime,
                    maxPrepTime,
                    matchedRecipes.stream()
                            .map(r -> r.getTitle().toLowerCase())
                            .collect(Collectors.toSet()),
                    missing);

            matchedRecipes.addAll(aiRecipes);
        }

        // ✅ Convert recipes to DTOs before caching
        List<RecipeDTO> dtoRecipes = matchedRecipes.stream()
                .map(RecipeDTO::new)
                .collect(Collectors.toList());

        // ✅ Cache with prep times
        return recipeCacheService.addMatchedRecipes(dtoRecipes, pantryIngredients, minPrepTime, maxPrepTime);
    }

    /**
     * Returns next batch of RecipeDTOs for a token.
     * Falls back to AI if cache is exhausted.
     */
    @Transactional
    public List<RecipeDTO> getNextBatch(String token, int batchSize) throws Exception {
        List<RecipeDTO> batch = recipeCacheService.getNextRecipes(token, batchSize);

        if (batch.isEmpty() && recipeCacheService.isExhausted(token)) {
            System.out.println("Cache exhausted for token: " + token + " → falling back to AI");

            // pull filters & ingredients from cache
            int minPrep = recipeCacheService.getMinPrepTime(token);
            int maxPrep = recipeCacheService.getMaxPrepTime(token);
            List<PantryIngredient> pantryIngredients = recipeCacheService.getCachedIngredients(token);
            Set<String> alreadySeenTitles = recipeCacheService.getPresentedTitles(token);

            // fetch fresh recipes (guaranteed valid titles)
            List<Recipe> aiRecipes = fetchValidAIRecipes(
                    pantryIngredients,
                    minPrep,
                    maxPrep,
                    alreadySeenTitles,
                    batchSize);

            List<RecipeDTO> dtoRecipes = aiRecipes.stream()
                    .map(RecipeDTO::new)
                    .toList();

            // add to cache
            recipeCacheService.addMoreRecipes(token, dtoRecipes);

            // now retrieve
            return recipeCacheService.getNextRecipes(token, batchSize);
        }

        return batch;
    }

    /**
     * Helper to repeatedly fetch AI recipes until enough valid ones are collected.
     */
    private List<Recipe> fetchValidAIRecipes(
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime,
            Set<String> alreadySeenTitles,
            int required) throws Exception {

        List<Map<String, Object>> ingredientsForAI = pantryIngredients.stream()
                .map(pi -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("ingredientName", pi.getIngredientName());
                    m.put("quantity", pi.getQuantity());
                    m.put("unit", pi.getUnit());
                    return m;
                })
                .collect(Collectors.toList());

        List<Recipe> validRecipes = new ArrayList<>();
        int attempts = 0;

        while (validRecipes.size() < required && attempts < MAX_AI_RETRIES) {
            attempts++;

            int stillNeeded = required - validRecipes.size();
            List<Recipe> aiRecipes = recipeAIService.generateRecipes(
                    ingredientsForAI,
                    minPrepTime,
                    maxPrepTime,
                    alreadySeenTitles,
                    stillNeeded);

            // ✅ Normalize excluded titles once
            Set<String> lowerExcluded = alreadySeenTitles.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            // ✅ filter out: null/empty titles AND ones already seen
            aiRecipes = aiRecipes.stream()
                    .filter(r -> r.getTitle() != null && !r.getTitle().trim().isEmpty())
                    .filter(r -> !lowerExcluded.contains(r.getTitle().toLowerCase()))
                    .toList();

            // ✅ Add to results and mark as seen
            validRecipes.addAll(aiRecipes);
            alreadySeenTitles.addAll(aiRecipes.stream()
                    .map(r -> r.getTitle().toLowerCase())
                    .toList());
        }

        return validRecipes;
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
                        (pi1, pi2) -> pi1));

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
        if (recipe.getIngredients() == null)
            return false;

        for (RecipeIngredient req : recipe.getIngredients()) {
            if (req.getIngredientName() == null)
                return false;

            String reqName = normalizeName(req.getIngredientName());
            String reqUnit = normalizeUnit(req.getUnit());

            PantryIngredient pantry = findPantryMatch(reqName, pantryMap);
            if (pantry == null)
                return false;

            double pantryQty = UnitConverter.convert(pantry.getQuantity(), pantry.getUnit(), reqUnit);
            if (pantryQty < req.getQuantity())
                return false;
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
        if (pantryMap.containsKey(reqName))
            return pantryMap.get(reqName);

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
