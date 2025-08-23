package com.pantrypilot.controller;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
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

    private static final int BATCH_SIZE = 3;

    // -------------------- DTO for frontend POST --------------------
    public static class MatchRequest {
        public List<PantryIngredient> ingredients;
        public int minPrepTime;
        public int maxPrepTime;
    }

    // -------------------- POST endpoint --------------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> getMatchingRecipes(@RequestBody MatchRequest request) throws Exception {

        List<PantryIngredient> pantryIngredients = request.ingredients != null ? request.ingredients : Collections.emptyList();
        int minPrepTime = request.minPrepTime;
        int maxPrepTime = request.maxPrepTime;

        System.out.println("Received pantry ingredients: " + pantryIngredients.size());
        System.out.println("Prep time filter: " + minPrepTime + " - " + maxPrepTime);

        // ✅ Service now returns token directly after caching
        String token = recipeMatcherService.matchRecipesWithCache(
                pantryIngredients, minPrepTime, maxPrepTime, BATCH_SIZE
        );

        // ✅ Fetch first batch of recipes
        List<Recipe> firstBatch = recipeMatcherService.getNextBatch(token, BATCH_SIZE);

        if (firstBatch.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "recipes", Collections.emptyList(),
                    "message", "No recipes found for given ingredients and prep time."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", firstBatch
        ));
    }

    // -------------------- GET next batch --------------------
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getNextRecipes(@PathVariable String token) {

        List<Recipe> nextBatch = recipeMatcherService.getNextBatch(token, BATCH_SIZE);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "recipes", nextBatch
        ));
    }
}
