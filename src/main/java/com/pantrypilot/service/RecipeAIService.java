package com.pantrypilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeAIService {

    @Value("${gemini.api.key}") // <-- Gemini key from application.properties
    private String geminiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecipeRepository recipeRepository;

    public List<Recipe> generateRecipes(
            List<Map<String, Object>> ingredients,
            int minPrepTime,
            int maxPrepTime,
            Set<String> excludedTitles,
            int count
    ) {
        try {
            if (ingredients == null || ingredients.isEmpty()) return Collections.emptyList();

            // 1. Build the AI prompt
            String prompt = buildPrompt(ingredients, minPrepTime, maxPrepTime, excludedTitles, count);
            System.out.println("Gemini Prompt: " + prompt);

            // 2. Create Gemini client
            Client client = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();

            // 3. Call Gemini API using SDK
            GenerateContentResponse resp = client.models.generateContent(
                    "gemini-1.5-flash",
                    prompt,
                    null   // options (temperature, maxOutputTokens) – null means defaults
            );

            if (resp == null || resp.text() == null || resp.text().isBlank()) {
                return Collections.emptyList();
            }

            String text = resp.text();

            // 4. Parse into Recipe objects
            List<Recipe> aiRecipes = parseAiResponse(text);

            // 5. Ensure bidirectional link for ingredients
            for (Recipe recipe : aiRecipes) {
                if (recipe.getIngredients() != null) {
                    for (RecipeIngredient ri : recipe.getIngredients()) {
                        ri.setRecipe(recipe);
                    }
                }
            }

            System.out.println("Gemini returned " + aiRecipes.size() + " recipes");

            // 6. Save to DB and return
            return recipeRepository.saveAll(aiRecipes);

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private String buildPrompt(List<Map<String, Object>> ingredients, int minPrepTime,
                               int maxPrepTime, Set<String> excludedTitles, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(count)
                .append(" recipes in a JSON array using these ingredients: ");

        List<String> formattedIngredients = new ArrayList<>();
        for (Map<String, Object> ing : ingredients) {
            formattedIngredients.add(
                    ing.get("quantity") + " " + ing.get("unit") + " " + ing.get("ingredientName")
            );
        }
        sb.append(String.join(", ", formattedIngredients)).append(". ");
        sb.append("You may optionally add common veg household items. ");

        if (!excludedTitles.isEmpty()) {
            sb.append("Do not use titles: ").append(String.join(", ", excludedTitles)).append(". ");
        }

        sb.append("Each recipe must have: title, instructions, prepTime (minutes), ingredients (ingredientName, quantity, unit). ");
        sb.append("Return ONLY a valid JSON array, no extra text.");

        return sb.toString();
    }

    private String cleanJson(String text) {
    // remove // comments
    text = text.replaceAll("//.*", "");
    // remove /* ... */ comments
    text = text.replaceAll("/\\*.*?\\*/", "");
    return text;
}

    /**
     * Parse Gemini text response into Recipe objects
     */
    private List<Recipe> parseAiResponse(String text) throws Exception {
    // Extract JSON array safely
    int start = text.indexOf("[");
    int end = text.lastIndexOf("]");
    if (start == -1 || end == -1 || start >= end) return Collections.emptyList();

    String jsonArray = cleanJson(text.substring(start, end + 1));
    List<Recipe> recipes = objectMapper.readValue(jsonArray, new TypeReference<List<Recipe>>() {});

    // ✅ Clean up invalid quantities in ingredients
    for (Recipe recipe : recipes) {
        if (recipe.getIngredients() == null) continue;

        recipe.setIngredients(
            recipe.getIngredients().stream()
                .filter(ri -> {
                    try {
                        Double.parseDouble(String.valueOf(ri.getQuantity())); // keep if numeric
                        return true;
                    } catch (Exception e) {
                        System.out.println("Skipping ingredient due to invalid quantity: " + ri.getIngredientName());
                        return false;
                    }
                })
                .collect(Collectors.toList())
        );
    }

    return recipes;
}

}
