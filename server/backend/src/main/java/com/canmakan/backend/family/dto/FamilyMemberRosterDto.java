package com.canmakan.backend.family.dto;

import java.util.List;

/**
 * UC12 roster row for {@code GET /api/families/me/members}.
 * Aligns with the web {@code FamilyMember} client type.
 */
public record FamilyMemberRosterDto(
    long memberId,
    String profileName,
    String relationship,
    String ageGroup,
    List<String> commonRequirements,
    List<String> restrictions,
    String source,
    String maskedEmail
) {
    public static final String SOURCE_REGISTERED = "REGISTERED_USER";
    public static final String SOURCE_DEPENDANT = "DEPENDANT_PROFILE";
    public static final String AGE_GROUP_UNSPECIFIED = "UNSPECIFIED";
}
