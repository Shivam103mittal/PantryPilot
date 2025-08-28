package com.pantrypilot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.model.Ingredient;
import com.pantrypilot.repository.IngredientRepository;
import com.pantrypilot.service.IngredientImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngredientImageServiceImpl implements IngredientImageService {

    @Value("${unsplash.access.key}")
    private String unsplashAccessKey;

    private static final String FALLBACK_MESSAGE = "Image not available";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache: ingredientName -> imageUrl/message
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private final IngredientRepository ingredientRepository;

    public IngredientImageServiceImpl(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    public String getImageUrl(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return FALLBACK_MESSAGE;
        }

        String key = ingredientName.trim().toLowerCase();

        // ✅ 1. Check cache first
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        String imageUrl = FALLBACK_MESSAGE;

        try {
            String url = "https://api.unsplash.com/search/photos?query="
                    + key
                    + " food ingredient"
                    + "&client_id=" + unsplashAccessKey
                    + "&per_page=1";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");

            if (results.isArray() && results.size() > 0) {
                imageUrl = results.get(0).path("urls").path("small").asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ✅ 2. Store in cache
        cache.put(key, imageUrl);

        // ✅ 3. Persist in DB
        Optional<Ingredient> existingIngredient = ingredientRepository.findByNameIgnoreCase(key);
        if (existingIngredient.isPresent()) {
            Ingredient ingredient = existingIngredient.get();
            ingredient.setImageUrl(imageUrl);
            ingredientRepository.save(ingredient);
        } else {
            Ingredient newIngredient = new Ingredient();
            newIngredient.setName(key);
            newIngredient.setImageUrl(imageUrl);
            ingredientRepository.save(newIngredient);
        }

        return imageUrl;
    }

}
