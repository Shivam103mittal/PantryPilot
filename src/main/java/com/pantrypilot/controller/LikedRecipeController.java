package com.pantrypilot.controller;

import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.User;
import com.pantrypilot.service.LikedRecipeService;
import com.pantrypilot.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikedRecipeController {

    private final LikedRecipeService likedRecipeService;
    private final RecipeRepository recipeRepository;

    // Utility method to get authenticated user
    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping("/{recipeId}")
    public ResponseEntity<String> likeRecipe(@PathVariable Long recipeId) {
        User user = getCurrentUser();
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RuntimeException("Recipe not found"));

        likedRecipeService.likeRecipe(user, recipe);
        return ResponseEntity.ok("Recipe liked successfully");
    }

    @DeleteMapping("/{recipeId}")
    public ResponseEntity<String> unlikeRecipe(@PathVariable Long recipeId) {
        User user = getCurrentUser();
        System.out.println("DELETE request by user: " + user.getEmail() + " for recipe: " + recipeId);
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RuntimeException("Recipe not found"));

        likedRecipeService.unlikeRecipe(user, recipe);
        return ResponseEntity.ok("Recipe unliked successfully");
    }

    @GetMapping
    public ResponseEntity<List<Recipe>> getLikedRecipes() {
        User user = getCurrentUser();
        List<Recipe> likedRecipes = likedRecipeService.getLikedRecipes(user);
        return ResponseEntity.ok(likedRecipes);
    }
}
