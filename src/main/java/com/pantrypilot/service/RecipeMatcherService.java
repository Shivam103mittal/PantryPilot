// Save this code!
package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.dto.IngredientDTO;
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
    private final IngredientImageService ingredientImageService;

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

        // This set will only contain titles from the initial DB match, before AI is
        // called
        Set<String> initialDbTitles = matchedRecipes.stream()
                .map(Recipe::getTitle)
        .filter(Objects::nonNull)                  // ignore null titles
        .map(String::toLowerCase)
        .collect(Collectors.toSet());

        Set<String> aiTitlesGeneratedInThisCall = new HashSet<>();

        if (matchedRecipes.size() < batchSize) {
            int missing = batchSize - matchedRecipes.size();

            List<Recipe> aiRecipes = fetchValidAIRecipes(
                    pantryIngredients,
                    minPrepTime,
                    maxPrepTime,
                    initialDbTitles, // Pass only the DB titles as excluded for the first AI call
                    missing);

            matchedRecipes.addAll(aiRecipes);
            aiTitlesGeneratedInThisCall.addAll(aiRecipes.stream()
                    .map(r -> r.getTitle().toLowerCase())
                    .collect(Collectors.toList())); // Use collect for clarity
        }

        // Convert recipes to DTOs before caching
        // Convert recipes to DTOs with enriched ingredient images
        List<RecipeDTO> dtoRecipes = matchedRecipes.stream()
                .map(recipe -> {
                    List<IngredientDTO> enrichedIngredients = recipe.getIngredients().stream()
                            .map(ri -> {
                                IngredientDTO dto = new IngredientDTO(ri);
                                String url = ingredientImageService.getImageUrl(dto.getIngredientName());
                                dto.setImageUrl(url);
                                return dto;
                            })
                            .collect(Collectors.toList());

                    return new RecipeDTO(recipe, enrichedIngredients);
                })
                .collect(Collectors.toList());

        // Cache with prep times. The RecipeCacheService.addMatchedRecipes will handle
        // de-duplication
        // and populate its internal allCachedTitles set with the unique titles from
        // dtoRecipes.
        return recipeCacheService.addMatchedRecipes(dtoRecipes, pantryIngredients, minPrepTime, maxPrepTime,
                aiTitlesGeneratedInThisCall);
    }

    /**
     * Returns next batch of RecipeDTOs for a token.
     * Falls back to AI if cache is exhausted.
     */
    @Transactional
    public List<RecipeDTO> getNextBatch(String token, int batchSize) throws Exception {
        if (token == null || batchSize <= 0)
            return Collections.emptyList();

        List<RecipeDTO> resultBatch = new ArrayList<>();

        // 1️⃣ Pull recipes already cached
        // The getNextRecipes method internally handles presentedTitles and AI limits
        List<RecipeDTO> cacheBatch = recipeCacheService.getNextRecipes(token, batchSize);
        resultBatch.addAll(cacheBatch);

        int filled = resultBatch.size();

        // 2️⃣ Determine remaining AI quota
        int aiGeneratedSoFar = recipeCacheService.getAiGeneratedCount(token);
        int aiRemaining = Math.max(0, 5 - aiGeneratedSoFar); // Max 5 AI recipes per session
        int remainingSlots = batchSize - filled;

        // 3️⃣ If batch not full and AI quota available, fetch AI recipes
        if (remainingSlots > 0 && aiRemaining > 0) {
            int toFetchFromAI = Math.min(remainingSlots, aiRemaining);

            int minPrep = recipeCacheService.getMinPrepTime(token);
            int maxPrep = recipeCacheService.getMaxPrepTime(token);
            List<PantryIngredient> pantry = recipeCacheService.getCachedIngredients(token);

            // IMPORTANT: Get ALL titles already cached (including those not yet presented)
            // to pass to AI for exclusion.
            Set<String> allExcludedTitles = recipeCacheService.getAllCachedTitles(token);

            List<Recipe> aiRecipes = fetchValidAIRecipes(pantry, minPrep, maxPrep, allExcludedTitles, toFetchFromAI);
            List<RecipeDTO> dtoAI = aiRecipes.stream()
                    .map(recipe -> {
                        List<IngredientDTO> enrichedIngredients = recipe.getIngredients().stream()
                                .map(ri -> {
                                    IngredientDTO dto = new IngredientDTO(ri);
                                    String url = ingredientImageService.getImageUrl(dto.getIngredientName());
                                    dto.setImageUrl(url);
                                    return dto;
                                })
                                .collect(Collectors.toList());

                        return new RecipeDTO(recipe, enrichedIngredients);
                    })
                    .collect(Collectors.toList());

            // Add AI recipes to cache. The addMoreRecipes method will handle de-duplication
            // against its internal allCachedTitles set.
            recipeCacheService.addMoreRecipes(token, dtoAI, true);

            // Re-attempt to get recipes from cache after new ones are added
            // This is crucial because `getNextRecipes` might have returned an empty list
            // if `currentIndex` was at the end before `addMoreRecipes` was called.
            // We now get up to `remainingSlots` new items, which will be the newly added AI
            // recipes.
            List<RecipeDTO> freshAIFromCache = recipeCacheService.getNextRecipes(token, remainingSlots);
            resultBatch.addAll(freshAIFromCache);
            filled = resultBatch.size();
        }

        // 4️⃣ Check if AI quota is exhausted and if no more recipes are available
        // Note: The IllegalStateException should only be thrown if we genuinely ran out
        // of recipes
        // and hit the AI limit, preventing an infinite loop.
        // It's better to return an empty list or a list smaller than batchSize if no
        // more are available,
        // rather than throwing an exception for every "no more recipes" scenario.
        if (resultBatch.isEmpty() && recipeCacheService.isExhausted(token)) {
            // This indicates no more recipes can be provided for this token.
            // The frontend should handle an empty list as "no more results."
            System.out.println("No more recipes available for token: " + token);
            return Collections.emptyList();
        }

        // 5️⃣ Return at most batchSize recipes
        return (resultBatch.size() > batchSize) ? resultBatch.subList(0, batchSize) : resultBatch;
    }

    /**
     * Helper to repeatedly fetch AI recipes until enough valid ones are collected.
     * Now accepts a Set of ALL titles to exclude, not just already presented.
     */
    private List<Recipe> fetchValidAIRecipes(
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime,
            Set<String> allExcludedTitles, // Renamed for clarity
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

        // Create a working set for titles already considered in this specific AI
        // fetching loop
        Set<String> localExcludedTitles = new HashSet<>(allExcludedTitles);

        while (validRecipes.size() < required && attempts < MAX_AI_RETRIES) {
            attempts++;

            int stillNeeded = required - validRecipes.size();
            List<Recipe> aiRecipes = recipeAIService.generateRecipes(
                    ingredientsForAI,
                    minPrepTime,
                    maxPrepTime,
                    localExcludedTitles, // Pass the growing set of excluded titles to AI
                    stillNeeded);

            // Filter out: null/empty titles AND ones already seen (locally or globally)
            List<Recipe> newUniqueRecipes = aiRecipes.stream()
                    .filter(r -> r.getTitle() != null && !r.getTitle().trim().isEmpty())
                    .filter(r -> !localExcludedTitles.contains(r.getTitle().toLowerCase())) // Use localExcludedTitles
                    .collect(Collectors.toList()); // Collect to a list for adding

            // Add to results and update local excluded titles for subsequent AI calls in
            // this loop
            validRecipes.addAll(newUniqueRecipes);
            newUniqueRecipes.stream()
                    .map(Recipe::getTitle)
        .filter(Objects::nonNull)                  // <- add this
        .map(String::toLowerCase)
        .forEach(localExcludedTitles::add);
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