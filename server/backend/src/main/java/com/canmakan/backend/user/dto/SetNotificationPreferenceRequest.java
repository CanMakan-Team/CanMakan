package com.canmakan.backend.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/users/me/preferences/notifications.
 *
 * @author Amelia
 */
public record SetNotificationPreferenceRequest(
    @NotNull(message = "notificationsEnabled is required.")
    Boolean notificationsEnabled
) {
}
