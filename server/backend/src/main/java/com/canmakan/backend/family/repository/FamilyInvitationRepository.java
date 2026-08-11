package com.canmakan.backend.family.repository;

import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.InvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for family invitations.
 * 
 * @author Amelia
 */
public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {

    // Find family invitation by invitation token
    Optional<FamilyInvitation> findByInvitationToken(String invitationToken);

    // Check if family invitation exists by invitation token
    boolean existsByInvitationToken(String invitationToken);

    // Check if family invitation exists by invite code
    boolean existsByInviteCode(String inviteCode);

    // Find family invitation by family id, invited email and status
    @Query("""
        select fi from FamilyInvitation fi
        where fi.familyId = :familyId
          and fi.invitedEmail = :email
          and fi.status = :status
        """)
    Optional<FamilyInvitation> findByFamilyIdAndInvitedEmailAndStatus(
        @Param("familyId") Long familyId,
        @Param("email") String invitedEmail,
        @Param("status") InvitationStatus status
    );

    // Find family invitations by invited email and status ordered by created at descending
    @Query("""
        select fi from FamilyInvitation fi
        where fi.invitedEmail = :email
          and fi.status = :status
        order by fi.createdAt desc
        """)
    List<FamilyInvitation> findByInvitedEmailAndStatusOrderByCreatedAtDesc(
        @Param("email") String invitedEmail,
        @Param("status") InvitationStatus status
    );

    // Find pending family invitation by family id and invited email
    default Optional<FamilyInvitation> findPendingByFamilyAndEmail(Long familyId, String email) {
        return findByFamilyIdAndInvitedEmailAndStatus(familyId, email, InvitationStatus.PENDING);
    }

    // Find pending family invitations by invited email ordered by created at descending
    default List<FamilyInvitation> findPendingByEmail(String email) {
        return findByInvitedEmailAndStatusOrderByCreatedAtDesc(email, InvitationStatus.PENDING);
    }
}
