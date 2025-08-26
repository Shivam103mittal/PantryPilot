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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeAIService {

    @Value("${gemini.api.key}") // <-- Gemini key from application.properties
    private String geminiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RecipeRepository recipeRepository;

    @Transactional
    public List<Recipe> generateRecipes(
            List<Map<String, Object>> ingredients,
            int minPrepTime,
            int maxPrepTime,
            Set<String> excludedTitles,
            int count) {
        try {
            if (ingredients == null || ingredients.isEmpty())
                return Collections.emptyList();

            // 1. Build the AI prompt
            String prompt = buildPrompt(ingredients, minPrepTime, maxPrepTime, excludedTitles, count);
            System.out.println("Gemini Prompt: " + prompt);

            // 2. Create Gemini client
            Client client = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();

            // 3. Call Gemini API
            GenerateContentResponse resp = client.models.generateContent(
                    "gemini-1.5-flash",
                    prompt,
                    null);

            if (resp == null || resp.text() == null || resp.text().isBlank()) {
                return Collections.emptyList();
            }

            String text = resp.text();

            System.out.println(text);

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

            // 6. Save to DB and return (with pre-check to avoid duplicate insert failure)
            List<Recipe> finalRecipes = new ArrayList<>();

            for (Recipe recipe : aiRecipes) {
                Optional<Recipe> existing = recipeRepository.findByTitle(recipe.getTitle());

                if (existing.isPresent()) {
                    System.out.println("Duplicate recipe found, using existing: " + recipe.getTitle());
                    finalRecipes.add(existing.get());
                } else {
                    for (RecipeIngredient ri : recipe.getIngredients()) {
                        ri.setRecipe(recipe);
                    }
                    Recipe saved = recipeRepository.save(recipe);
                    finalRecipes.add(saved);
                }
            }

            return finalRecipes;

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
                    ing.get("quantity") + " " + ing.get("unit") + " " + ing.get("ingredientName"));
        }
        sb.append(String.join(", ", formattedIngredients)).append(". ");

        int targetTotal = (int) Math.floor(ingredients.size() / 0.8);
        int requiredExtra = targetTotal - ingredients.size();

        sb.append("You may add atmost ").append(requiredExtra)
          .append(" additional common veg household ingredients beyond the provided ones and not more than that. ");

        if (!excludedTitles.isEmpty()) {
            sb.append("Strictly do not use previous titles ");
        }

        sb.append(
                "Return ONLY a valid JSON array, no extra text. with these fields: title, instructions, prepTime (minutes between ")
                .append(minPrepTime).append(" and ").append(maxPrepTime)
                .append("), ingredients (ingredientName(string), quantity(int), unit(pcs,tbsp,l,g)). ");
        return sb.toString();
    }


    private String cleanJson(String text) {
        // remove // comments
        text = text.replaceAll("(?m)//.*$", "");
        // remove /* ... */ comments (including multiline)
        text = text.replaceAll("(?s)/\\*.*?\\*/", "");
        return text.trim();
    }

    /**
     * Parse Gemini text response into Recipe objects
     */
    private List<Recipe> parseAiResponse(String text) throws Exception {
        // Extract JSON array safely
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");
        if (start == -1 || end == -1 || start >= end)
            return Collections.emptyList();

        String jsonArray = cleanJson(text.substring(start, end + 1));
        objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);

        List<Recipe> recipes = objectMapper.readValue(jsonArray, new TypeReference<List<Recipe>>() {
        });

        // ✅ Clean up invalid quantities in ingredients
        for (Recipe recipe : recipes) {
            if (recipe.getIngredients() == null)
                continue;

            recipe.setIngredients(
                    recipe.getIngredients().stream()
                            .filter(ri -> {
                                try {
                                    Double.parseDouble(String.valueOf(ri.getQuantity())); // keep if numeric
                                    return true;
                                } catch (Exception e) {
                                    System.out.println(
                                            "Skipping ingredient due to invalid quantity: " + ri.getIngredientName());
                                    return false;
                                }
                            })
                            .collect(Collectors.toList()));
        }

        return recipes;
    }

}
