package com.canmakan.backend.notification;

import com.canmakan.backend.notification.dto.UserNotificationResponse;
import com.canmakan.backend.notification.exception.NotificationNotFoundException;
import com.canmakan.backend.shared.security.AuthUserChecker;
import com.canmakan.backend.shared.security.AuthUserDetails;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account notifications inbox.
 *
 * @author Amelia
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public List<UserNotificationResponse> listMine(
            @AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        List<UserNotificationResponse> notifications = notificationService.listMine(userId);
        log.info("GET /notifications/me → 200 count={}", notifications.size());
        return notifications;
    }

    @PostMapping("/me/read")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        notificationService.markAllRead(userId);
        log.info("POST /notifications/me/read → 204");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMine(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable("id") long notificationId) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        notificationService.deleteMine(userId, notificationId);
        log.info("DELETE /notifications/{} → 204", notificationId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NotificationNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }
}
