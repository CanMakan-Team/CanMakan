package com.canmakan.backend.family.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Membership of a user in a family circle.
 * Family Admin capability is {@code PRIMARY_ADMIN} here — not a platform JWT role.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "family_members")
public class FamilyMember {

    public static final String ROLE_PRIMARY_ADMIN = "PRIMARY_ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    @Valid
    @NotNull(message = "Family membership id is required.")
    @EmbeddedId
    private FamilyMemberId id;

    @NotBlank(message = "Member role is required.")
    @Size(max = 30, message = "Member role must be at most 30 characters.")
    @Pattern(
            regexp = "PRIMARY_ADMIN|MEMBER",
            message = "Member role must be PRIMARY_ADMIN or MEMBER.")
    @Column(name = "member_role", nullable = false, length = 30)
    private String memberRole;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private Instant joinedAt;

    public Long getFamilyId() {
        return id == null ? null : id.getFamilyId();
    }

    public Long getUserId() {
        return id == null ? null : id.getUserId();
    }

    // UC8 family member id
    // @Embeddable to use as a composite primary key
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class FamilyMemberId implements Serializable {

        @NotNull(message = "Family id is required.")
        @Column(name = "family_id", nullable = false)
        private Long familyId;

        @NotNull(message = "User id is required.")
        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FamilyMemberId that)) {
                return false;
            }
            return Objects.equals(familyId, that.familyId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(familyId, userId);
        }
    }
}
