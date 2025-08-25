package com.pantrypilot.dto;

import com.pantrypilot.model.Recipe;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeDTO {
    private Long id;
    private String title;
    private String instructions;
    private int prepTime;
    private List<IngredientDTO> ingredients;

    // Convenience constructor to convert Recipe → RecipeDTO
    public RecipeDTO(Recipe recipe) {
        this.id = recipe.getId();
        this.title = recipe.getTitle();
        this.instructions = recipe.getInstructions();
        this.prepTime = recipe.getPrepTime();
        if (recipe.getIngredients() != null) {
            this.ingredients = recipe.getIngredients().stream()
                    .map(IngredientDTO::new)
                    .collect(Collectors.toList());
        }
    }
}
