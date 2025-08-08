package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.RecipeCacheService;
import com.pantrypilot.service.RecipeMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/matching-recipes")
@RequiredArgsConstructor
public class RecipeMatcherController {

    private final RecipeMatcherService recipeMatcherService;
    private final RecipeCacheService recipeCacheService;

    private static final int BATCH_SIZE = 3;

    /**
     * POST endpoint to accept pantry ingredients and return first batch of matched recipes + token.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> getMatchingRecipes(
            @RequestBody List<PantryIngredient> pantryIngredients) {

        System.out.println("Received pantry ingredients: " + pantryIngredients.size());

        List<Recipe> matchedRecipes = recipeMatcherService.matchRecipes(pantryIngredients);
        System.out.println("Matched recipes count: " + matchedRecipes.size());

        String token = recipeCacheService.addMatchedRecipes(matchedRecipes);

        // Use a mutable list to add AI fallback recipes if needed
        List<Recipe> firstBatch = new ArrayList<>(recipeCacheService.getNextRecipes(token, BATCH_SIZE));
        System.out.println("First batch size before AI fallback: " + firstBatch.size());

        // Add AI fallback recipes if first batch is less than BATCH_SIZE
        if (firstBatch.size() < BATCH_SIZE) {
            int missing = BATCH_SIZE - firstBatch.size();
            System.out.println("Fetching " + missing + " AI fallback recipes");
            List<Recipe> aiRecipes = callAiFallback(missing);
            firstBatch.addAll(aiRecipes);
        }

        System.out.println("Returning total " + firstBatch.size() + " recipes");

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", firstBatch
        ));
    }

    /**
     * GET endpoint to return next batch of recipes for the given token.
     */
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getNextRecipes(
            @PathVariable String token) {

        System.out.println("Received token: " + token);

        List<Recipe> nextBatch = new ArrayList<>(recipeCacheService.getNextRecipes(token, BATCH_SIZE));
        System.out.println("Next batch size before AI fallback: " + nextBatch.size());

        int fetchedCount = nextBatch.size();

        if (fetchedCount < BATCH_SIZE) {
            int missing = BATCH_SIZE - fetchedCount;
            System.out.println("Fetching " + missing + " AI fallback recipes");
            List<Recipe> aiRecipes = callAiFallback(missing);
            nextBatch.addAll(aiRecipes);
        }

        System.out.println("Returning total " + nextBatch.size() + " recipes");

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", nextBatch
        ));
    }

    /**
     * Stub for AI fallback call - returns dummy/generated recipes.
     * Replace this with real AI integration later.
     */
    private List<Recipe> callAiFallback(int count) {
        List<Recipe> aiGenerated = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Recipe r = new Recipe();
            r.setId(Long.valueOf(-i)); // negative IDs indicate AI-generated recipes
            r.setTitle("AI Generated Recipe " + i);
            r.setInstructions("This is an AI generated recipe as no matching recipes were found.");
            r.setIngredients(Collections.emptyList());
            aiGenerated.add(r);
        }
        return aiGenerated;
    }
}
