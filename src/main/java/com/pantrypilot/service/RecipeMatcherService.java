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

    private static final int MAX_AI_RETRIES = 3;

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

        Set<String> initialDbTitles = matchedRecipes.stream()
                .map(Recipe::getTitle)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> aiTitlesGeneratedInThisCall = new HashSet<>();

        if (matchedRecipes.size() < batchSize) {
            int missing = batchSize - matchedRecipes.size();

            List<Recipe> aiRecipes = fetchValidAIRecipes(
                    pantryIngredients,
                    minPrepTime,
                    maxPrepTime,
                    initialDbTitles,
                    missing);

            matchedRecipes.addAll(aiRecipes);
            aiTitlesGeneratedInThisCall.addAll(aiRecipes.stream()
                    .map(r -> r.getTitle().toLowerCase())
                    .collect(Collectors.toList()));
        }

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

        return recipeCacheService.addMatchedRecipes(dtoRecipes, pantryIngredients, minPrepTime, maxPrepTime,
                aiTitlesGeneratedInThisCall);
    }

    @Transactional
    public List<RecipeDTO> getNextBatch(String token, int batchSize) throws Exception {
        if (token == null || batchSize <= 0)
            return Collections.emptyList();

        List<RecipeDTO> resultBatch = new ArrayList<>();

        List<RecipeDTO> cacheBatch = recipeCacheService.getNextRecipes(token, batchSize);
        resultBatch.addAll(cacheBatch);

        int filled = resultBatch.size();

        int aiGeneratedSoFar = recipeCacheService.getAiGeneratedCount(token);
        int aiRemaining = Math.max(0, 5 - aiGeneratedSoFar);
        int remainingSlots = batchSize - filled;

        if (remainingSlots > 0 && aiRemaining > 0) {
            int toFetchFromAI = Math.min(remainingSlots, aiRemaining);

            int minPrep = recipeCacheService.getMinPrepTime(token);
            int maxPrep = recipeCacheService.getMaxPrepTime(token);
            List<PantryIngredient> pantry = recipeCacheService.getCachedIngredients(token);

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

            recipeCacheService.addMoreRecipes(token, dtoAI, true);

            List<RecipeDTO> freshAIFromCache = recipeCacheService.getNextRecipes(token, remainingSlots);
            resultBatch.addAll(freshAIFromCache);
            filled = resultBatch.size();
        }

        if (resultBatch.isEmpty() && recipeCacheService.isExhausted(token)) {
            System.out.println("No more recipes available for token: " + token);
            return Collections.emptyList();
        }

        return (resultBatch.size() > batchSize) ? resultBatch.subList(0, batchSize) : resultBatch;
    }

    /**
     * Helper to repeatedly fetch AI recipes until enough valid ones are collected.
     * NOW INCLUDES INGREDIENT COUNT VALIDATION
     */
    private List<Recipe> fetchValidAIRecipes(
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime,
            Set<String> allExcludedTitles,
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

        // Calculate max allowed ingredients (same logic as in RecipeAIService)
        int providedIngredientCount = pantryIngredients.size();
        int maxAllowedIngredients = (int) Math.floor(providedIngredientCount / 0.75);
        
        System.out.println("Ingredient validation: provided=" + providedIngredientCount + 
                         ", max allowed=" + maxAllowedIngredients);

        List<Recipe> validRecipes = new ArrayList<>();
        int attempts = 0;

        Set<String> localExcludedTitles = new HashSet<>(allExcludedTitles);

        while (validRecipes.size() < required && attempts < MAX_AI_RETRIES) {
            attempts++;

            int stillNeeded = required - validRecipes.size();
            List<Recipe> aiRecipes = recipeAIService.generateRecipes(
                    ingredientsForAI,
                    minPrepTime,
                    maxPrepTime,
                    localExcludedTitles,
                    stillNeeded);

            // Filter with ALL validations:
            // 1. Non-null/empty title
            // 2. Not already excluded
            // 3. Ingredient count within limit
            // 4. Has valid ingredients list
            List<Recipe> newUniqueRecipes = aiRecipes.stream()
                    .filter(r -> r.getTitle() != null && !r.getTitle().trim().isEmpty())
                    .filter(r -> !localExcludedTitles.contains(r.getTitle().toLowerCase()))
                    .filter(r -> {
                        if (r.getIngredients() == null || r.getIngredients().isEmpty()) {
                            System.out.println("Rejecting recipe '" + r.getTitle() + "': no ingredients");
                            return false;
                        }
                        
                        int recipeIngredientCount = r.getIngredients().size();
                        boolean valid = recipeIngredientCount <= maxAllowedIngredients;
                        
                        if (!valid) {
                            System.out.println("Rejecting recipe '" + r.getTitle() + 
                                             "': has " + recipeIngredientCount + 
                                             " ingredients, max allowed is " + maxAllowedIngredients);
                        }
                        
                        return valid;
                    })
                    .collect(Collectors.toList());

            validRecipes.addAll(newUniqueRecipes);
            newUniqueRecipes.stream()
                    .map(Recipe::getTitle)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .forEach(localExcludedTitles::add);
        }

        System.out.println("AI fetch completed: " + validRecipes.size() + " valid recipes after " + attempts + " attempts");
        return validRecipes;
    }

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

            
        if (reqName.endsWith("es") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 2))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 2));
        }
        if (reqName.endsWith("s") && pantryMap.containsKey(reqName.substring(0, reqName.length() - 1))) {
            return pantryMap.get(reqName.substring(0, reqName.length() - 1));
        }


        for (String pantryKey : pantryMap.keySet()) {
            if (pantryKey.contains(reqName) || reqName.contains(pantryKey)) {
                return pantryMap.get(pantryKey);
            }
        }

        return null;
    }
}