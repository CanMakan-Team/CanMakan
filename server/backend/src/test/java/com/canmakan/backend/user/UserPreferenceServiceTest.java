package com.canmakan.backend.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.family.model.UserPreference;
import com.canmakan.backend.family.repository.UserPreferenceRepository;
import com.canmakan.backend.user.dto.NotificationPreferenceResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Notification preference works the same for an account with no family circle as for a
 * family member -- these tests never set up any family/membership state.
 *
 * @author Amelia
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceService: account-level notification preference")
class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    private UserPreferenceService userPreferenceService;

    @BeforeEach
    void setUp() {
        userPreferenceService = new UserPreferenceService(userPreferenceRepository);
    }

    @Test
    @DisplayName("getNotificationPreference defaults to disabled when no preference row exists")
    void getNotificationPreferenceDefaultsToDisabled() {
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.empty());

        NotificationPreferenceResponse response = userPreferenceService.getNotificationPreference(10L);

        assertFalse(response.notificationsEnabled());
    }

    @Test
    @DisplayName("getNotificationPreference reflects a stored disabled preference")
    void getNotificationPreferenceReflectsStoredValue() {
        UserPreference stored = new UserPreference();
        stored.setUserId(10L);
        stored.setNotificationsEnabled(false);
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.of(stored));

        NotificationPreferenceResponse response = userPreferenceService.getNotificationPreference(10L);

        assertFalse(response.notificationsEnabled());
    }

    @Test
    @DisplayName("setNotificationPreference persists a new preference row on first use")
    void setNotificationPreferenceCreatesRow() {
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.empty());
        when(userPreferenceRepository.saveAndFlush(any(UserPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse response =
            userPreferenceService.setNotificationPreference(10L, false);

        assertFalse(response.notificationsEnabled());
        verify(userPreferenceRepository).saveAndFlush(any(UserPreference.class));
    }

    @Test
    @DisplayName("setNotificationPreference updates an existing preference row")
    void setNotificationPreferenceUpdatesRow() {
        UserPreference stored = new UserPreference();
        stored.setUserId(10L);
        stored.setNotificationsEnabled(true);
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.of(stored));
        when(userPreferenceRepository.saveAndFlush(any(UserPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse response =
            userPreferenceService.setNotificationPreference(10L, false);

        assertFalse(response.notificationsEnabled());
        assertFalse(stored.getNotificationsEnabled());
    }
}
