package com.pantrypilot.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ingredientName;

    private double quantity;

    private String unit;

     @ManyToOne
     @JoinColumn(name = "recipe_id")
     @JsonBackReference
     private Recipe recipe;
}
