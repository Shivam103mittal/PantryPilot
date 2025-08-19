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

    // -------------------- DTO for frontend POST --------------------
    public static class MatchRequest {
        public List<PantryIngredient> ingredients;
        public int minPrepTime;
        public int maxPrepTime;
    }

    // -------------------- POST endpoint --------------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> getMatchingRecipes(
            @RequestBody MatchRequest request) {

        List<PantryIngredient> pantryIngredients = request.ingredients;
        int minPrepTime = request.minPrepTime;
        int maxPrepTime = request.maxPrepTime;

        System.out.println("Received pantry ingredients: " + pantryIngredients.size());
        System.out.println("Prep time filter: " + minPrepTime + " - " + maxPrepTime);

        // Match recipes using pantry ingredients + prep time
        List<Recipe> matchedRecipes = recipeMatcherService.matchRecipesWithPrepTime(
                pantryIngredients, minPrepTime, maxPrepTime
        );

        // Add matched recipes to cache and get a token
        String token = recipeCacheService.addMatchedRecipes(matchedRecipes);

        // Return first batch
        List<Recipe> firstBatch = new ArrayList<>(recipeCacheService.getNextRecipes(token, BATCH_SIZE));

        // AI fallback if batch is small
        if (firstBatch.size() < BATCH_SIZE) {
            int missing = BATCH_SIZE - firstBatch.size();
            firstBatch.addAll(callAiFallback(missing));
        }

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", firstBatch
        ));
    }

    // -------------------- GET next batch --------------------
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getNextRecipes(@PathVariable String token) {

        System.out.println("Received token: " + token);

        List<Recipe> nextBatch = new ArrayList<>(recipeCacheService.getNextRecipes(token, BATCH_SIZE));
        System.out.println("Next batch size before AI fallback: " + nextBatch.size());

        if (nextBatch.size() < BATCH_SIZE) {
            int missing = BATCH_SIZE - nextBatch.size();
            System.out.println("Fetching " + missing + " AI fallback recipes");
            nextBatch.addAll(callAiFallback(missing));
        }

        System.out.println("Returning total " + nextBatch.size() + " recipes");

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", nextBatch
        ));
    }

    // -------------------- AI fallback stub --------------------
    private List<Recipe> callAiFallback(int count) {
        List<Recipe> aiGenerated = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Recipe r = new Recipe();
            r.setId(Long.valueOf(-i)); // negative IDs indicate AI-generated
            r.setTitle("AI Generated Recipe " + i);
            r.setInstructions("This is an AI generated recipe as no matching recipes were found.");
            r.setIngredients(Collections.emptyList());
            aiGenerated.add(r);
        }
        return aiGenerated;
    }
}
