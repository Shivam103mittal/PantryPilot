package com.pantrypilot.repository;

import com.pantrypilot.model.LikedRecipe;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikedRecipeRepository extends JpaRepository<LikedRecipe, Long> {

    // Get all liked recipes for a user
    List<LikedRecipe> findByUser(User user);

    // Check if a user already liked a recipe
    Optional<LikedRecipe> findByUserAndRecipe(User user, Recipe recipe);

    // Delete liked recipe by user + recipe (for unlike functionality)
    void deleteByUserAndRecipe(User user, Recipe recipe);
}
