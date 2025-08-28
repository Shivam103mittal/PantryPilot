package com.pantrypilot.dto;

import com.pantrypilot.model.Recipe;
import com.pantrypilot.service.IngredientImageService;
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

    /**
     * Constructor that converts Recipe → RecipeDTO and automatically populates
     * imageUrl
     */
    public RecipeDTO(Recipe recipe, IngredientImageService imageService) {
        this.id = recipe.getId();
        this.title = recipe.getTitle();
        this.instructions = recipe.getInstructions();
        this.prepTime = recipe.getPrepTime();
        if (recipe.getIngredients() != null) {
            this.ingredients = recipe.getIngredients().stream()
                    .map(ri -> {
                        IngredientDTO dto = new IngredientDTO(ri);
                        dto.setImageUrl(imageService.getImageUrl(dto.getIngredientName()));
                        return dto;
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Optional: Constructor if you already have enriched IngredientDTO list
     */
    public RecipeDTO(Recipe recipe, List<IngredientDTO> ingredientDTOs) {
        this.id = recipe.getId();
        this.title = recipe.getTitle();
        this.instructions = recipe.getInstructions();
        this.prepTime = recipe.getPrepTime();
        this.ingredients = ingredientDTOs;
    }
}
