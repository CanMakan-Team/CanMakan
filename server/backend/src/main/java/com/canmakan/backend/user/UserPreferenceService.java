package com.canmakan.backend.user;

import com.canmakan.backend.family.model.UserPreference;
import com.canmakan.backend.family.repository.UserPreferenceRepository;
import com.canmakan.backend.user.dto.NotificationPreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-level user preferences that do not depend on family membership (e.g. an
 * individual account with no family circle uses this exactly the same way a family
 * member does). Backed by the same {@link UserPreference} row/table used for the
 * UC11 active scan profile, since both are per-user, not per-family, data.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;

    /**
     * Returns whether the caller currently allows CanMakan to post system notifications.
     * A user with no stored preference row yet gets the entity's default (disabled).
     */
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getNotificationPreference(long userId) {
        UserPreference preference = userPreferenceRepository.findById(userId)
            .orElseGet(UserPreference::new);
        return new NotificationPreferenceResponse(preference.getNotificationsEnabled());
    }

    /**
     * Persists the caller's notification preference, creating the preference row on first use.
     */
    @Transactional
    public NotificationPreferenceResponse setNotificationPreference(long userId, boolean enabled) {
        UserPreference preference = userPreferenceRepository.findById(userId)
            .orElseGet(() -> {
                UserPreference created = new UserPreference();
                created.setUserId(userId);
                return created;
            });
        preference.setNotificationsEnabled(enabled);
        userPreferenceRepository.saveAndFlush(preference);
        return new NotificationPreferenceResponse(preference.getNotificationsEnabled());
    }
}
