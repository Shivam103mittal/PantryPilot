package com.pantrypilot.service;

public interface IngredientImageService {
    /**
     * Return a URL for the given ingredient name (cached if previously fetched).
     */
    String getImageUrl(String ingredientName);
}
