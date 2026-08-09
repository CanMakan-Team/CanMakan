package com.canmakan.backend.family.dto;

/**
 * Result of searching for a user email before creating an invitation.
 * 
 * @author Amelia
 */
public record UserSearchResponse(
    Long userId,
    String displayName,
    String maskedEmail,
    String accountStatus,
    String familyLinkStatus
) {
    public static final String ACCOUNT_ACTIVE = "ACTIVE";
    public static final String ACCOUNT_INACTIVE = "INACTIVE";
    public static final String ACCOUNT_NOT_REGISTERED = "NOT_REGISTERED";

    public static final String LINK_NOT_LINKED = "NOT_LINKED";
    public static final String LINK_ALREADY_LINKED = "ALREADY_LINKED";
    public static final String LINK_PENDING = "PENDING";
}
