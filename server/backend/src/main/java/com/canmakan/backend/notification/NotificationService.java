package com.canmakan.backend.notification;

import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account inbox: list, upsert, mark read, and delete. Feature packages supply copy and type.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository userNotificationRepository;
    private final UserAccountRepository userAccountRepository;

    @Transactional
    public void upsert(
            long userId,
            NotificationType type,
            String referenceType,
            Long referenceId,
            String title,
            String body,
            String actionToken,
            Instant expiresAt) {
        Instant now = Instant.now();
        UserNotification row = userNotificationRepository
            .findByUserIdAndTypeAndReferenceTypeAndReferenceId(
                userId, type, referenceType, referenceId)
            .orElseGet(UserNotification::new);
        row.setUserId(userId);
        row.setType(type);
        row.setReferenceType(referenceType);
        row.setReferenceId(referenceId);
        row.setTitle(title);
        row.setBody(body);
        row.setActionToken(actionToken);
        row.setExpiresAt(expiresAt);
        row.setReadAt(null);
        row.setUpdatedAt(now);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        userNotificationRepository.saveAndFlush(row);
    }

    @Transactional
    public void deleteByReference(NotificationType type, String referenceType, Long referenceId) {
        userNotificationRepository.deleteByTypeAndReferenceTypeAndReferenceId(
            type, referenceType, referenceId);
    }

    @Transactional
    public List<UserNotificationResponse> listMine(long userId) {
        requireUser(userId);
        List<UserNotification> rows =
            userNotificationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<UserNotificationResponse> results = new ArrayList<>();
        Instant now = Instant.now();
        for (UserNotification row : rows) {
            boolean expired = row.getExpiresAt() != null && !row.getExpiresAt().isAfter(now);
            results.add(new UserNotificationResponse(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getBody(),
                row.getActionToken(),
                expired,
                row.getReadAt() != null,
                row.getUpdatedAt()
            ));
        }
        return results;
    }

    @Transactional
    public void markAllRead(long userId) {
        requireUser(userId);
        userNotificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void deleteMine(long userId, long notificationId) {
        UserNotification row = userNotificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification was not found."));
        userNotificationRepository.delete(row);
    }

    private void requireUser(long userId) {
        if (!userAccountRepository.existsById(userId)) {
            throw new AuthenticatedUserNotFoundException("Authenticated user was not found.");
        }
    }
}
