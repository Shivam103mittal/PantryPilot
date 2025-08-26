// Save this code!
package com.pantrypilot.service;

import com.pantrypilot.dto.RecipeDTO;
import com.pantrypilot.model.PantryIngredient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors; // Import Collectors

@Service
public class RecipeCacheService {

    private static class CacheEntry {
        List<RecipeDTO> recipes;
        List<PantryIngredient> pantryIngredients;
        int currentIndex;
        Set<String> presentedTitles;
        Set<String> aiTitles;          // track AI-generated recipe titles
        Set<String> allCachedTitles;   // NEW: Track all unique titles ever added to this cache entry
        long createdAt;
        int minPrepTime;
        int maxPrepTime;
        int aiRecipesServed;           // AI recipes already served

        CacheEntry(List<RecipeDTO> recipes,
                   List<PantryIngredient> pantryIngredients,
                   int minPrepTime,
                   int maxPrepTime,
                   Set<String> aiTitles) {
            this.recipes = new ArrayList<>();
            this.pantryIngredients = pantryIngredients != null ? new ArrayList<>(pantryIngredients) : new ArrayList<>();
            this.currentIndex = 0;
            this.presentedTitles = new HashSet<>();
            this.aiTitles = aiTitles != null ? new HashSet<>(aiTitles) : new HashSet<>();
            this.createdAt = System.currentTimeMillis();
            this.minPrepTime = minPrepTime;
            this.maxPrepTime = maxPrepTime;
            this.aiRecipesServed = 0;
            this.allCachedTitles = new HashSet<>(); // Initialize the new set

            // Add initial recipes, filtering for duplicates and populating allCachedTitles
            for (RecipeDTO recipe : recipes) {
                if (recipe.getTitle() != null && !this.allCachedTitles.contains(recipe.getTitle().toLowerCase())) {
                    this.recipes.add(recipe);
                    this.allCachedTitles.add(recipe.getTitle().toLowerCase());
                }
            }
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** Adds matched recipes DTOs + pantry ingredients + prep time filters to cache and returns a token. */
    public String addMatchedRecipes(List<RecipeDTO> matchedRecipes,
                                    List<PantryIngredient> pantryIngredients,
                                    int minPrepTime,
                                    int maxPrepTime,
                                    Set<String> aiTitles) {

        if (matchedRecipes == null) matchedRecipes = Collections.emptyList();
        
        String token = UUID.randomUUID().toString();
        // The CacheEntry constructor now handles initial de-duplication
        CacheEntry newEntry = new CacheEntry(matchedRecipes, pantryIngredients, minPrepTime, maxPrepTime, aiTitles);
        cache.put(token, newEntry);
        
        System.out.println("Adding matched recipes to cache, count: " + newEntry.recipes.size()); // Log actual count after de-dupe
        System.out.println("Generated token: " + token);
        return token;
    }

    /** Appends new RecipeDTOs to an existing cache entry. If fromAI is true, adds titles to aiTitles. */
    public void addMoreRecipes(String token, List<RecipeDTO> newRecipes, boolean fromAI) {
        if (token == null || newRecipes == null || newRecipes.isEmpty()) return;

        CacheEntry entry = cache.get(token);
        if (entry != null) {
            List<RecipeDTO> recipesToAdd = newRecipes.stream()
                .filter(recipe -> recipe.getTitle() != null && !entry.allCachedTitles.contains(recipe.getTitle().toLowerCase()))
                .collect(Collectors.toList());

            entry.recipes.addAll(recipesToAdd);
            for (RecipeDTO recipe : recipesToAdd) {
                entry.allCachedTitles.add(recipe.getTitle().toLowerCase());
                if (fromAI && recipe.getTitle() != null) {
                    entry.aiTitles.add(recipe.getTitle().toLowerCase());
                }
            }
            System.out.println("Appended " + recipesToAdd.size() + " unique recipes to cache token " + token + " fromAI=" + fromAI);
        }
    }

    /** Returns the next batch of recipes respecting AI limit of 5 per session. */
    public List<RecipeDTO> getNextRecipes(String token, int batchSize) {
        if (token == null || batchSize <= 0) return Collections.emptyList();

        CacheEntry entry = cache.get(token);
        if (entry == null) {
            System.out.println("Cache entry not found for token: " + token);
            return Collections.emptyList();
        }

        List<RecipeDTO> nextBatch = new ArrayList<>();
        int originalIndex = entry.currentIndex; // Store original index

        while (nextBatch.size() < batchSize && entry.currentIndex < entry.recipes.size()) {
            RecipeDTO recipe = entry.recipes.get(entry.currentIndex);
            entry.currentIndex++; // Increment currentIndex regardless of whether it's added to batch

            if (recipe.getTitle() == null) continue;

            String titleKey = recipe.getTitle().toLowerCase();

            // Check if already presented in THIS batch or previous batches for this token
            if (entry.presentedTitles.contains(titleKey)) continue;

            boolean isAI = entry.aiTitles.contains(titleKey);

            if (isAI && entry.aiRecipesServed >= 5) {
                // If AI limit reached, and this is an AI recipe, skip it.
                // We don't increment entry.currentIndex here as it was already done.
                continue;
            }

            nextBatch.add(recipe);
            entry.presentedTitles.add(titleKey);
            if (isAI) entry.aiRecipesServed++;
        }

        System.out.println("Returning " + nextBatch.size() + " recipes for token " + token);
        return nextBatch;
    }

    public List<PantryIngredient> getCachedIngredients(String token) {
        CacheEntry entry = cache.get(token);
        return entry != null ? new ArrayList<>(entry.pantryIngredients) : Collections.emptyList();
    }

    public Set<String> getPresentedTitles(String token) {
        CacheEntry entry = cache.get(token);
        return entry != null ? new HashSet<>(entry.presentedTitles) : Collections.emptySet();
    }

    // NEW: Get all unique titles currently in the cache for this token
    public Set<String> getAllCachedTitles(String token) {
        CacheEntry entry = cache.get(token);
        return entry != null ? new HashSet<>(entry.allCachedTitles) : Collections.emptySet();
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
        if (token != null) cache.remove(token);
    }

    public void evictExpiredEntries(long ttlMillis) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().createdAt) > ttlMillis);
    }

    public boolean isExhausted(String token) {
        CacheEntry entry = cache.get(token);
        return entry == null || entry.currentIndex >= entry.recipes.size();
    }

    public int getAiGeneratedCount(String token) {
        CacheEntry entry = cache.get(token);
        return (entry != null) ? entry.aiRecipesServed : 0;
    }
}