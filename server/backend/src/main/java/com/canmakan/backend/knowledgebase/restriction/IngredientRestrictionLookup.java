package com.canmakan.backend.knowledgebase.restriction;

import java.util.Set;

/**
 * Read-only access to approved dietary conflicts for a standardised ingredient.
 *
 * @author YangMaowei
 */
public interface IngredientRestrictionLookup {

    Set<String> findApprovedConflictCodes(String standardisedIngredientName);
}
