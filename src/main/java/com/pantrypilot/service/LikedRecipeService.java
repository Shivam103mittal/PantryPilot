package com.pantrypilot.service;

import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.User;
import java.util.List;

public interface LikedRecipeService {
    void likeRecipe(User user, Recipe recipe);
    void unlikeRecipe(User user, Recipe recipe);
    List<Recipe> getLikedRecipes(User user);
}
