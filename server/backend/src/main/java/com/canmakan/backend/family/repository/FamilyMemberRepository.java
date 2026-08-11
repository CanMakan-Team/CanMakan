package com.canmakan.backend.family.repository;

import com.canmakan.backend.family.model.FamilyMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for family membership rows.
 * 
 * @author Amelia
 * @author Khai
 */
public interface FamilyMemberRepository
    extends JpaRepository<FamilyMember, FamilyMember.FamilyMemberId> {

    // Check if user is a member of a family
    boolean existsByIdUserId(Long userId);

    // Find family member by user id
    @Query("""
        select fm from FamilyMember fm
        where fm.id.userId = :userId
        """)
    Optional<FamilyMember> findMembershipByUserId(@Param("userId") Long userId);

    // Find active family members by family id
    @Query("""
        select fm from FamilyMember fm
        where fm.id.familyId = :familyId and fm.isActive = true
        """)
    List<FamilyMember> findActiveMembersByFamilyId(@Param("familyId") Long familyId);

    @Query("""
        select count(fm) from FamilyMember fm
        where fm.id.familyId = :familyId
            and fm.memberRole = 'PRIMARY_ADMIN'
            and fm.isActive = true
        """)
    long countActivePrimaryAdmins(@Param("familyId") Long familyId);
}
