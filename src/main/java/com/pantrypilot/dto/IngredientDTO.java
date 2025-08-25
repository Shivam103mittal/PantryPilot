package com.pantrypilot.dto;

import com.pantrypilot.model.RecipeIngredient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngredientDTO {
    private Long id;
    private String ingredientName;
    private double quantity;
    private String unit;

    // Convenience constructor to convert RecipeIngredient → IngredientDTO
    public IngredientDTO(RecipeIngredient ri) {
        this.id = ri.getId();
        this.ingredientName = ri.getIngredientName();
        this.quantity = ri.getQuantity();
        this.unit = ri.getUnit();
    }
}
