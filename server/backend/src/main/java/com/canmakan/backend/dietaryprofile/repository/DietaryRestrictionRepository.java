package com.canmakan.backend.dietaryprofile.repository;

import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for the dietary restriction catalog (reference data).
 * 
 * @author Amelia Wong
 */
public interface DietaryRestrictionRepository extends JpaRepository<DietaryRestriction, Long> {

    @Query("select d from DietaryRestriction d order by d.displayName asc")
    List<DietaryRestriction> findAllOrderedByDisplayName();

    @Query("select d from DietaryRestriction d where lower(d.code) = lower(:code)")
    Optional<DietaryRestriction> findByCodeIgnoreCase(@Param("code") String code);
}
