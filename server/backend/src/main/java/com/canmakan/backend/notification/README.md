# notification

Per-user inbox for invite cards and other account notifications.

## Package layout (small feature)

```
notification/
  NotificationController.java
  README.md
  service/NotificationService.java
  repository/UserNotificationRepository.java
  model/UserNotification.java
  model/NotificationType.java
  dto/UserNotificationResponse.java
  exception/NotificationNotFoundException.java
```

Controller stays at the root. `dto/` and `exception/` were added in F19 / P4; F20 nested `service/` and `repository/`.
