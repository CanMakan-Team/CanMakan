package com.canmakan.backend.family.model;

/**
 * Family context for the authenticated caller ({@code GET /api/families/me} and create response).
 * 
 * @author Amelia
 */
public record FamilyMeResponse(
        Long familyId,
        String familyName,
        String memberRole,
        Long selfProfileId,
        Long createdByUserId
) {
}
