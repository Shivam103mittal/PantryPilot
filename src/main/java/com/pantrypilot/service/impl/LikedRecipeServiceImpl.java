package com.pantrypilot.service.impl;

import com.pantrypilot.model.LikedRecipe;
import com.pantrypilot.model.Recipe;
import com.pantrypilot.model.User;
import com.pantrypilot.repository.LikedRecipeRepository;
import com.pantrypilot.service.LikedRecipeService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikedRecipeServiceImpl implements LikedRecipeService {

    private final LikedRecipeRepository likedRecipeRepository;

    @Override
    public void likeRecipe(User user, Recipe recipe) {
        
        likedRecipeRepository.findByUserAndRecipe(user, recipe)
                .orElseGet(() -> likedRecipeRepository.save(
                        LikedRecipe.builder()
                                .user(user)
                                .recipe(recipe)
                                .build()
                ));
    }

    @Override
    @Transactional
    public void unlikeRecipe(User user, Recipe recipe) {
        likedRecipeRepository.deleteByUserAndRecipe(user, recipe);
    }

    @Override
    public List<Recipe> getLikedRecipes(User user) {
        return likedRecipeRepository.findByUser(user)
                .stream()
                .map(LikedRecipe::getRecipe)
                .collect(Collectors.toList());
    }
}
