package com.pantrypilot.service;

import com.pantrypilot.model.Recipe;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecipeCacheService {

    private static class CacheEntry {
        List<Recipe> recipes;
        int currentIndex;

        CacheEntry(List<Recipe> recipes) {
            this.recipes = recipes;
            this.currentIndex = 0;
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Adds a new matched recipe list and returns a token.
     */
    public String addMatchedRecipes(List<Recipe> matchedRecipes) {
        System.out.println("Adding matched recipes to cache, count: " + matchedRecipes.size());
        String token = UUID.randomUUID().toString();
        cache.put(token, new CacheEntry(matchedRecipes));
        System.out.println("Generated token: " + token);
        return token;
    }

    /**
     * Returns the next batch of recipes for the given token.
     */
    public List<Recipe> getNextRecipes(String token, int batchSize) {
        CacheEntry entry = cache.get(token);
        if (entry == null) {
            System.out.println("Cache entry not found for token: " + token);
            return Collections.emptyList();
        }

        int start = entry.currentIndex;
        int end = Math.min(start + batchSize, entry.recipes.size());
        System.out.println("Fetching recipes from " + start + " to " + end + " for token " + token);

        if (start >= end) {
            System.out.println("No more recipes to return for token: " + token);
            return Collections.emptyList();
        }

        List<Recipe> nextBatch = new ArrayList<>(entry.recipes.subList(start, end));
        entry.currentIndex = end;

        if (entry.currentIndex >= entry.recipes.size()) {
            System.out.println("All recipes served, removing token: " + token);
            cache.remove(token);
        }

        System.out.println("Returning " + nextBatch.size() + " recipes for token " + token);
        return nextBatch;
    }

    public void removeToken(String token) {
        cache.remove(token);
    }
}
