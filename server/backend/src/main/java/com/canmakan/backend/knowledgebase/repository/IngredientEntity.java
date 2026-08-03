package com.canmakan.backend.knowledgebase.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public IngredientEntity() {
    }

    public IngredientEntity(String ingredientName, String parentAllergen, String rootAllergen, Boolean isChemicalAlias) {
        this.ingredientName = ingredientName;
        this.parentAllergen = parentAllergen;
        this.rootAllergen = rootAllergen;
        this.isChemicalAlias = isChemicalAlias;
    }

    public Long getId() {
        return id;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public String getParentAllergen() {
        return parentAllergen;
    }

    public String getRootAllergen() {
        return rootAllergen;
    }

    public Boolean getIsChemicalAlias() {
        return isChemicalAlias;
    }
}
