package com.canmakan.backend.notification;

import java.time.Instant;

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
