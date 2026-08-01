package com.canmakan.backend.dietaryprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository acts as single access point for all tables related to dietary profile feature
 * Using query to keep functions in singular repository
 * 
 * @author Amelia Wong
 */
public interface DietaryProfileRepository extends JpaRepository<DietaryProfile, Long> {

    @Query("select d from DietaryRestriction d order by d.displayName asc")
    List<DietaryRestriction> findAllRestrictions();

    @Query("select pr from ProfileRestriction pr join fetch pr.dietaryRestriction dr where pr.dietaryProfile.id = :profileId")
    List<ProfileRestriction> findProfileRestrictionsByProfileId(@Param("profileId") Long profileId);

    @Query("select d from DietaryRestriction d where d.id = :restrictionId")
    Optional<DietaryRestriction> findRestrictionById(@Param("restrictionId") Long restrictionId);

    @Query("select dp from DietaryProfile dp where dp.family.id = :familyId order by dp.profileName asc")
    List<DietaryProfile> findProfilesByFamilyId(@Param("familyId") Long familyId);
}
