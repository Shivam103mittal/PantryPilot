package com.pantrypilot.service;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecipeCacheService {

    private static class CacheEntry {
        List<Recipe> recipes;
        List<PantryIngredient> pantryIngredients;
        int currentIndex;
        Set<String> presentedTitles;
        long createdAt;

        CacheEntry(List<Recipe> recipes, List<PantryIngredient> pantryIngredients) {
            this.recipes = new ArrayList<>(recipes);
            this.pantryIngredients = pantryIngredients != null ? new ArrayList<>(pantryIngredients) : new ArrayList<>();
            this.currentIndex = 0;
            this.presentedTitles = new HashSet<>();
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Adds matched recipes + pantry ingredients to cache and returns a token.
     */
    public String addMatchedRecipes(List<Recipe> matchedRecipes, List<PantryIngredient> pantryIngredients) {
        if (matchedRecipes == null) matchedRecipes = Collections.emptyList();
        System.out.println("Adding matched recipes to cache, count: " + matchedRecipes.size());
        String token = UUID.randomUUID().toString();
        cache.put(token, new CacheEntry(matchedRecipes, pantryIngredients));
        System.out.println("Generated token: " + token);
        return token;
    }

    /**
     * Appends new recipes to existing cache entry.
     * Ensures RecipeIngredient → Recipe link is set.
     */
    public void addMatchedRecipes(List<Recipe> newRecipes, List<PantryIngredient> pantryIngredients, String token) {
        if (newRecipes == null || newRecipes.isEmpty() || token == null) return;

        CacheEntry entry = cache.get(token);
        if (entry != null) {
            // Ensure RecipeIngredient back-reference
            for (Recipe recipe : newRecipes) {
                if (recipe.getIngredients() != null) {
                    for (RecipeIngredient ri : recipe.getIngredients()) {
                        ri.setRecipe(recipe);
                    }
                }
            }

            entry.recipes.addAll(newRecipes);

            // Optionally update pantry ingredients if provided
            if (pantryIngredients != null && !pantryIngredients.isEmpty()) {
                entry.pantryIngredients = new ArrayList<>(pantryIngredients);
            }

            System.out.println("Appended " + newRecipes.size() + " recipes to cache token " + token);
        }
    }

    /**
     * Returns the next batch of recipes for the given token.
     */
    public List<Recipe> getNextRecipes(String token, int batchSize) {
        if (token == null || batchSize <= 0) return Collections.emptyList();

        CacheEntry entry = cache.get(token);
        if (entry == null) {
            System.out.println("Cache entry not found for token: " + token);
            return Collections.emptyList();
        }

        int start = entry.currentIndex;
        int end = Math.min(start + batchSize, entry.recipes.size());
        if (start >= end) return Collections.emptyList();

        List<Recipe> nextBatch = new ArrayList<>(entry.recipes.subList(start, end));

        // Track shown recipes
        for (Recipe recipe : nextBatch) {
            if (recipe.getTitle() != null) {
                entry.presentedTitles.add(recipe.getTitle().toLowerCase());
            }
        }

        entry.currentIndex = end;
        System.out.println("Returning " + nextBatch.size() + " recipes for token " + token);
        return nextBatch;
    }

    public List<PantryIngredient> getCachedIngredients(String token) {
        CacheEntry entry = cache.get(token);
        if (entry == null) return Collections.emptyList();
        return new ArrayList<>(entry.pantryIngredients);
    }

    public Set<String> getPresentedTitles(String token) {
        CacheEntry entry = cache.get(token);
        if (entry == null) return Collections.emptySet();
        return new HashSet<>(entry.presentedTitles);
    }

    public void removeToken(String token) {
        if (token != null) cache.remove(token);
    }

    /**
     * Evict expired cache entries older than given TTL (ms).
     */
    public void evictExpiredEntries(long ttlMillis) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().createdAt) > ttlMillis);
    }
}
