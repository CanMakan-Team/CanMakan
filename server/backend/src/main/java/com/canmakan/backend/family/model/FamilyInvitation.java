package com.canmakan.backend.family.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pending or resolved invitation for a person to join a family circle.
 * Claim attaches a {@link FamilyMember} row; creation does not.
 *
 * @author Amelia
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "family_invitations")
public class FamilyInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @NotNull
    @Column(name = "invited_by_user_id", nullable = false)
    private Long invitedByUserId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "invited_email", nullable = false, length = 255)
    private String invitedEmail;

    @NotBlank
    @Size(max = 30)
    @Column(name = "relationship", nullable = false, length = 30)
    private String relationship;

    @NotBlank
    @Size(max = 100)
    @Column(name = "invitation_token", nullable = false, unique = true, length = 100)
    private String invitationToken;

    @NotBlank
    @Size(max = 12)
    @Column(name = "invite_code", nullable = false, unique = true, length = 12)
    private String inviteCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
