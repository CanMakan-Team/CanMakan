package com.canmakan.backend.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.canmakan.backend.notification.model.NotificationType;
import com.canmakan.backend.notification.model.UserNotification;

/**
 * Persistence for the account notifications inbox.
 *
 * @author Amelia
 */
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    Optional<UserNotification> findByUserIdAndTypeAndReferenceTypeAndReferenceId(
        Long userId,
        NotificationType type,
        String referenceType,
        Long referenceId
    );

    List<UserNotification> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

    void deleteByTypeAndReferenceTypeAndReferenceId(
        NotificationType type,
        String referenceType,
        Long referenceId
    );

    @Modifying
    @Query("""
        update UserNotification n
        set n.readAt = :now
        where n.userId = :userId
          and n.readAt is null
        """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
