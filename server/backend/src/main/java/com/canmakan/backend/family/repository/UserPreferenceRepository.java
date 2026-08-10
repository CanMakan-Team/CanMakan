package com.canmakan.backend.family.repository;

import com.canmakan.backend.family.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for user preferences (UC11 active profile).
 *
 * @author Amelia
 */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    java.util.List<UserPreference> findByActiveProfileId(Long activeProfileId);
}
