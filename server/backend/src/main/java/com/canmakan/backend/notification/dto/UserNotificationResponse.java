package com.canmakan.backend.notification.dto;

import java.time.Instant;

import com.canmakan.backend.notification.model.NotificationType;

/**
 * Inbox card for any feature. {@code actionToken} is set when the card has an in-app action.
 *
 * @author Amelia
 */
public record UserNotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String body,
    String actionToken,
    boolean expired,
    boolean read,
    Instant updatedAt
) {
}
