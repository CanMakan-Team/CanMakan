package com.canmakan.backend.family.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.canmakan.backend.family.model.Family;

/**
 * Persistence for family circles.
 *
 * @author Amelia
 */
public interface FamilyRepository extends JpaRepository<Family, Long> {
}
