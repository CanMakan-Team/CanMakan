package com.canmakan.backend.dietaryprofile.repository;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link DietaryProfile} rows and profile-scoped lookups.
 * 
 * @author Amelia Wong
 */
public interface DietaryProfileRepository extends JpaRepository<DietaryProfile, Long> {

    @Query("""
        select dp from DietaryProfile dp
        where dp.family.id = :familyId and dp.active = true
        order by dp.profileName asc
        """)
    List<DietaryProfile> findProfilesByFamilyId(@Param("familyId") Long familyId);

    @Query("""
        select dp from DietaryProfile dp
        where dp.family.id = :familyId
        order by dp.profileName asc
        """)
    List<DietaryProfile> findAllProfilesByFamilyId(@Param("familyId") Long familyId);

    @Query("""
        select dp from DietaryProfile dp
        where dp.family.id = :familyId and dp.linkedUser is null and dp.active = true
        order by dp.profileName asc
        """)
    List<DietaryProfile> findDependantProfilesByFamilyId(@Param("familyId") Long familyId);

    @Query("""
        select distinct dp from DietaryProfile dp
        left join fetch dp.linkedUser
        left join fetch dp.profileRestrictions pr
        left join fetch pr.dietaryRestriction
        where dp.linkedUser.id in :userIds
        """)
    List<DietaryProfile> findByLinkedUserIdInWithRestrictions(
        @Param("userIds") Collection<Long> userIds
    );

    @Query("""
        select distinct dp from DietaryProfile dp
        left join fetch dp.profileRestrictions pr
        left join fetch pr.dietaryRestriction
        where dp.family.id = :familyId and dp.linkedUser is null and dp.active = true
        order by dp.profileName asc
        """)
    List<DietaryProfile> findActiveDependantsByFamilyIdWithRestrictions(
        @Param("familyId") Long familyId
    );

    @Query("""
        select dp from DietaryProfile dp
        where dp.family.id = :familyId and dp.linkedUser is null
        order by dp.profileName asc
        """)
    List<DietaryProfile> findAllDependantProfilesByFamilyId(@Param("familyId") Long familyId);

    @Query("""
        select distinct dp from DietaryProfile dp
        left join fetch dp.profileRestrictions pr
        left join fetch pr.dietaryRestriction
        where dp.family.id = :familyId and dp.linkedUser is null
        order by dp.profileName asc
        """)
    List<DietaryProfile> findAllDependantsByFamilyIdWithRestrictions(
        @Param("familyId") Long familyId
    );

    Optional<DietaryProfile> findByLinkedUser_Id(Long linkedUserId);

    @Query("""
        select dp from DietaryProfile dp
        left join fetch dp.family
        left join fetch dp.linkedUser
        where dp.id = :profileId
        """)
    Optional<DietaryProfile> findByIdWithFamilyAndLinkedUser(@Param("profileId") Long profileId);
}
