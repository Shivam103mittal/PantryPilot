package com.pantrypilot.service.impl;

import com.pantrypilot.model.PantryIngredient;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.RecipeIngredient;
import com.pantrypilot.repository.RecipeRepository;
import com.pantrypilot.service.RecipeService;
import com.pantrypilot.util.UnitConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;

    @Autowired
    public RecipeServiceImpl(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public Recipe saveRecipe(Recipe recipe) {
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            ingredient.setRecipe(recipe);
        }
        return recipeRepository.save(recipe);
    }

    @Override
    public void deleteRecipeById(Long id) {
        recipeRepository.deleteById(id);
    }

    @Override
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    @Override
    public void clearAllRecipes() {
        recipeRepository.deleteAll();
    }

    @Override
    public Recipe getRecipeById(Long id) {
        return recipeRepository.findById(id).orElse(null);
    }

    @Override
    public List<Recipe> saveAll(List<Recipe> recipes) {
        List<Recipe> saved = new ArrayList<>();
        for (Recipe r : recipes) {
            saved.add(saveRecipe(r));
        }
        return saved;
    }

    @Override
    public List<Recipe> getRecipesByPrepTimeAndIngredients(
            int minPrepTime,
            int maxPrepTime,
            List<PantryIngredient> pantryIngredients) {
        Set<String> ingredientNames = pantryIngredients.stream()
                .map(pi -> pi.getIngredientName().toLowerCase())
                .collect(Collectors.toSet());

        // Step 1: DB fetch (prepTime + ingredient names)
        List<Recipe> candidateRecipes = recipeRepository.findByPrepTimeAndIngredients(
                minPrepTime, maxPrepTime, ingredientNames);

        // Step 2: In-memory filtering (quantity/unit)
        List<Recipe> filteredRecipes = new ArrayList<>();
        for (Recipe recipe : candidateRecipes) {
            boolean allIngredientsAvailable = true;

            for (RecipeIngredient ri : recipe.getIngredients()) {
                Optional<PantryIngredient> matchingPantry = pantryIngredients.stream()
                        .filter(pi -> pi.getIngredientName().equalsIgnoreCase(ri.getIngredientName()))
                        .findFirst();

                if (matchingPantry.isEmpty()) {
                    allIngredientsAvailable = false;
                    break;
                }

                PantryIngredient pi = matchingPantry.get();
                double availableQty = UnitConverter.convert(pi.getQuantity(), pi.getUnit(), ri.getUnit());
                if (availableQty < ri.getQuantity()) {
                    allIngredientsAvailable = false;
                    break;
                }
            }

            if (allIngredientsAvailable)
                filteredRecipes.add(recipe);
        }

        return filteredRecipes;
    }

    @Override
    public Recipe saveAIRecipe(Map<String, Object> aiRecipe) {
        String title = (String) aiRecipe.get("title");

        // 🚫 Skip invalid recipes
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        // ✅ Check for duplicates
        Optional<Recipe> existing = recipeRepository.findByTitle(title);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 🆕 Create new recipe
        Recipe recipe = new Recipe();
        recipe.setTitle(title);
        recipe.setInstructions((String) aiRecipe.get("instructions"));

        // Ingredients from AI response
        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) aiRecipe.get("ingredients");
        List<RecipeIngredient> recipeIngredients = ingredients.stream()
                .map(ing -> {
                    RecipeIngredient ri = new RecipeIngredient();
                    ri.setIngredientName((String) ing.get("ingredientName"));
                    ri.setQuantity(Double.parseDouble(ing.get("quantity").toString()));
                    ri.setUnit((String) ing.get("unit"));
                    ri.setRecipe(recipe); // back-reference
                    return ri;
                })
                .toList();

        recipe.setIngredients(recipeIngredients);

        return recipeRepository.save(recipe);
    }

}
