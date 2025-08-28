package com.pantrypilot.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ingredient", indexes = {
        @Index(name = "idx_ingredient_name", columnList = "name")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uc_ingredient_name", columnNames = { "name" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Canonical ingredient name. We will normalize names (trim + lowercase)
     * before storing/searching to avoid duplicates like "Onion" vs "onion ".
     */
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    /**
     * URL of an image for this ingredient (e.g. from Unsplash/Pexels).
     * Keep fairly long to accommodate provider URLs.
     */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;
}
