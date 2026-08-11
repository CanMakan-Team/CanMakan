package com.canmakan.backend.family.repository;

import com.canmakan.backend.family.model.Family;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for family circles.
 * 
 * @author Amelia
 */
public interface FamilyRepository extends JpaRepository<Family, Long> {
}
