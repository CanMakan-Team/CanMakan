package com.canmakan.backend.knowledgebase.restriction;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed lookup for approved ingredient restriction conflicts.
 *
 * @author YangMaowei
 */
@Repository
public class JpaIngredientRestrictionLookup implements IngredientRestrictionLookup {

    private static final String APPROVED_CONFLICT_QUERY = """
            SELECT dr.code
            FROM ingredient_restrictions ir
            JOIN ingredients i ON i.id = ir.ingredient_id
            JOIN dietary_restrictions dr ON dr.id = ir.dietary_restriction_id
            WHERE LOWER(i.ingredient_name) = LOWER(:ingredientName)
              AND ir.review_status = 'APPROVED'
              AND ir.rule_effect = 'CONFLICT'
            ORDER BY dr.code
            """;

    private final EntityManager entityManager;

    public JpaIngredientRestrictionLookup(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Set<String> findApprovedConflictCodes(String standardisedIngredientName) {
        if (standardisedIngredientName == null || standardisedIngredientName.isBlank()) {
            return Set.of();
        }

        List<String> codes = entityManager.createNativeQuery(APPROVED_CONFLICT_QUERY)
                .setParameter("ingredientName", standardisedIngredientName.trim())
                .getResultList();
        return Set.copyOf(new LinkedHashSet<>(codes));
    }
}
