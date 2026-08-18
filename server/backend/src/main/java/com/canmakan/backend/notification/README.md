# notification

Per-user inbox for invite cards and other account notifications.

## Package layout (small feature)

```
notification/
  NotificationController.java
  NotificationService.java
  UserNotification.java
  UserNotificationRepository.java
  NotificationType.java
  dto/UserNotificationResponse.java
  exception/NotificationNotFoundException.java
```

`dto/` and `exception/` were added once response and not-found types left the root (F19 / P4).
Keep the remaining types flat unless the package grows past ~8–10 types.
