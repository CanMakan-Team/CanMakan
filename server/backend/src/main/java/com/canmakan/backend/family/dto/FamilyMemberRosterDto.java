package com.canmakan.backend.family.dto;

import java.util.List;

/**
 * UC12 roster row for {@code GET /api/families/me/members}.
 * Aligns with the web {@code FamilyMember} client type.
 *
 * <p>{@code memberId} stays backward-compatible: linked user id for registered
 * members, profile id for dependants. Prefer {@code profileId} for manage APIs.
 * 
 * @author Amelia Wong
 */
public record FamilyMemberRosterDto(
    long memberId, // id of the member
    long profileId, // id of the profile
    Long linkedUserId, // id of the user who is the parent of the profile
    String profileName, // name of the profile
    String relationship, // SON, DAUGHTER, FATHER, MOTHER, etc.
    String ageGroup, // UNSPECIFIED, CHILD, ADULT
    List<String> commonRequirements, // allergies, intolerances, etc.
    List<String> restrictions, // dietary restrictions
    String source, // REGISTERED_USER, DEPENDANT_PROFILE
    String maskedEmail, // privacy-safe display of email address
    String memberRole, // ADMIN, MEMBER, DEPENDANT
    boolean profileActive // true if profile is active
) {
    public static final String SOURCE_REGISTERED = "REGISTERED_USER";
    public static final String SOURCE_DEPENDANT = "DEPENDANT_PROFILE";
    public static final String AGE_GROUP_UNSPECIFIED = "UNSPECIFIED";
}
