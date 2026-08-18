package com.canmakan.backend.user.repository;

import com.canmakan.backend.user.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for user preferences (UC11 active profile).
 *
 * @author Amelia
 */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    java.util.List<UserPreference> findByActiveProfileId(Long activeProfileId);
}
