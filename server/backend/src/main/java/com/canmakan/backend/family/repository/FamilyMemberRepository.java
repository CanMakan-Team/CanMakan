package com.canmakan.backend.family.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.canmakan.backend.family.model.FamilyMember;

/** UC8: Family member repository
 * 
 * @author Amelia
 */
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, FamilyMember.FamilyMemberId> {

    // UC8 check if family member exists by user id
    boolean existsByIdUserId(Long userId);

    // UC8 find family member by user id
    // @Query to use JPQL because the id is a composite primary key
    @Query("""
        select fm from FamilyMember fm
        where fm.id.userId = :userId
        """)
    Optional<FamilyMember> findMembershipByUserId(@Param("userId") Long userId);

    // UC6: Fetch List of Active Family Members by Family ID
    @Query("""
        select fm from FamilyMember fm
        where fm.id.familyId = :familyId and fm.isActive = true
        """)
    List<FamilyMember> findActiveMembersByFamilyId(@Param("familyId") Long familyId);
}
