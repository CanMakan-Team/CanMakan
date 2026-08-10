package com.canmakan.backend.family.model;

/**
 * Lifecycle status of a {@link FamilyInvitation}.
 * Persisted as the enum name in {@code family_invitations.status}.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
