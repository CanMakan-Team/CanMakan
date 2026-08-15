package com.canmakan.backend.user.dto;

/**
 * Whether the authenticated user currently allows CanMakan to post system notifications.
 *
 * @author Amelia
 */
public record NotificationPreferenceResponse(
    Boolean notificationsEnabled
) {
}
