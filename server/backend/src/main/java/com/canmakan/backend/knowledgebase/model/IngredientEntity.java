package com.canmakan.backend.knowledgebase.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA mapping for the {@code ingredients} catalog table.
 * Distinct from the domain {@link Ingredient} record used by the verdict engine.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "ingredients")
public class IngredientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_name", nullable = false, unique = true)
    private String ingredientName;

    @Column(name = "parent_allergen")
    private String parentAllergen;

    @Column(name = "root_allergen")
    private String rootAllergen;

    @Column(name = "is_chemical_alias")
    private Boolean isChemicalAlias;

    public IngredientEntity(
            String ingredientName,
            String parentAllergen,
            String rootAllergen,
            Boolean isChemicalAlias) {
        this.ingredientName = ingredientName;
        this.parentAllergen = parentAllergen;
        this.rootAllergen = rootAllergen;
        this.isChemicalAlias = isChemicalAlias;
    }
}
