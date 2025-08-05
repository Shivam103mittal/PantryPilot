package com.pantrypilot.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pantry_ingredient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PantryIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_name")
    private String ingredientName;

    @Column(name = "quantity")
    private String quantity;
}
