package com.canmakan.backend.dietaryprofile.repository;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
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
        where dp.family.id = :familyId and dp.linkedUser is null
        order by dp.profileName asc
        """)
    List<DietaryProfile> findDependantProfilesByFamilyId(@Param("familyId") Long familyId);

    Optional<DietaryProfile> findByLinkedUser_Id(Long linkedUserId);
}
