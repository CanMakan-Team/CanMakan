package com.canmakan.backend.family.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/families/me/preferences/notifications.
 *
 * @author Amelia
 */
public record SetNotificationPreferenceRequest(
    @NotNull(message = "notificationsEnabled is required.")
    Boolean notificationsEnabled
) {
}
