package com.canmakan.backend.family.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.canmakan.backend.family.model.Family;

/** UC8: Family repository
 * 
 * @author Amelia
 */
public interface FamilyRepository extends JpaRepository<Family, Long> {
    // UC8 find family by id
    Optional<Family> findById(Long id);

    // UC8 find family by name
    Optional<Family> findByFamilyName(String familyName);

    // UC8 find family by created by user id
    Optional<Family> findByCreatedByUserId(Long createdByUserId);
}
