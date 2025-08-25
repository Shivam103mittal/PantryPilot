package com.pantrypilot.service;

import com.pantrypilot.dto.RecipeDTO;
import com.pantrypilot.model.PantryIngredient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecipeCacheService {

    private static class CacheEntry {
        List<RecipeDTO> recipes;
        List<PantryIngredient> pantryIngredients;
        int currentIndex;
        Set<String> presentedTitles;
        long createdAt;
        int minPrepTime;
        int maxPrepTime;

        CacheEntry(List<RecipeDTO> recipes,
                List<PantryIngredient> pantryIngredients,
                int minPrepTime,
                int maxPrepTime) {
            this.recipes = new ArrayList<>(recipes);
            this.pantryIngredients = pantryIngredients != null ? new ArrayList<>(pantryIngredients) : new ArrayList<>();
            this.currentIndex = 0;
            this.presentedTitles = new HashSet<>();
            this.createdAt = System.currentTimeMillis();
            this.minPrepTime = minPrepTime;
            this.maxPrepTime = maxPrepTime;
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Adds matched recipes DTOs + pantry ingredients + prep time filters to cache
     * and returns a token.
     */
    public String addMatchedRecipes(List<RecipeDTO> matchedRecipes,
            List<PantryIngredient> pantryIngredients,
            int minPrepTime,
            int maxPrepTime) {
        if (matchedRecipes == null)
            matchedRecipes = Collections.emptyList();
        System.out.println("Adding matched recipes to cache, count: " + matchedRecipes.size());
        String token = UUID.randomUUID().toString();
        cache.put(token, new CacheEntry(matchedRecipes, pantryIngredients, minPrepTime, maxPrepTime));
        System.out.println("Generated token: " + token);
        return token;
    }

    /**
     * Appends new RecipeDTOs to an existing cache entry.
     */
    public void addMatchedRecipes(List<RecipeDTO> newRecipes,
            List<PantryIngredient> pantryIngredients,
            String token) {
        if (newRecipes == null || newRecipes.isEmpty() || token == null)
            return;

        CacheEntry entry = cache.get(token);
        if (entry != null) {
            entry.recipes.addAll(newRecipes);

            if (pantryIngredients != null && !pantryIngredients.isEmpty()) {
                entry.pantryIngredients = new ArrayList<>(pantryIngredients);
            }

            System.out.println("Appended " + newRecipes.size() + " recipes to cache token " + token);
        }
    }

    /**
     * Returns the next batch of recipes for the given token.
     */
    public List<RecipeDTO> getNextRecipes(String token, int batchSize) {
        if (token == null || batchSize <= 0)
            return Collections.emptyList();

        CacheEntry entry = cache.get(token);
        if (entry == null) {
            System.out.println("Cache entry not found for token: " + token);
            return Collections.emptyList();
        }

        int start = entry.currentIndex;
        int end = Math.min(start + batchSize, entry.recipes.size());
        if (start >= end)
            return Collections.emptyList();

        List<RecipeDTO> nextBatch = new ArrayList<>(entry.recipes.subList(start, end));

        for (RecipeDTO recipe : nextBatch) {
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
        if (entry == null)
            return Collections.emptyList();
        return new ArrayList<>(entry.pantryIngredients);
    }

    public Set<String> getPresentedTitles(String token) {
        CacheEntry entry = cache.get(token);
        if (entry == null)
            return Collections.emptySet();
        return new HashSet<>(entry.presentedTitles);
    }

    public int getMinPrepTime(String token) {
        CacheEntry entry = cache.get(token);
        return (entry != null) ? entry.minPrepTime : 0;
    }

    public int getMaxPrepTime(String token) {
        CacheEntry entry = cache.get(token);
        return (entry != null) ? entry.maxPrepTime : Integer.MAX_VALUE;
    }

    public void removeToken(String token) {
        if (token != null)
            cache.remove(token);
    }

    /**
     * Evict expired cache entries older than given TTL (ms).
     */
    public void evictExpiredEntries(long ttlMillis) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().createdAt) > ttlMillis);
    }

    public boolean isExhausted(String token) {
        CacheEntry entry = cache.get(token);
        if (entry == null)
            return true;
        return entry.currentIndex >= entry.recipes.size();
    }

    public void addMoreRecipes(String token, List<RecipeDTO> newRecipes) {
        CacheEntry entry = cache.get(token);
        if (entry != null && newRecipes != null && !newRecipes.isEmpty()) {
            entry.recipes.addAll(newRecipes);
        }
    }
}
