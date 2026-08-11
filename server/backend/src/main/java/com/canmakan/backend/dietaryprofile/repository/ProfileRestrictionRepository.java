package com.canmakan.backend.dietaryprofile.repository;

import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for profile-to-restriction link rows.    
 * 
 * @author Amelia Wong
 */
public interface ProfileRestrictionRepository
        extends JpaRepository<ProfileRestriction, ProfileRestrictionId> {

    @Query("""
        select pr from ProfileRestriction pr
        join fetch pr.dietaryRestriction dr
        where pr.dietaryProfile.id = :profileId
        """)
    List<ProfileRestriction> findByDietaryProfileId(@Param("profileId") Long profileId);
}
