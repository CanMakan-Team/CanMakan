package com.canmakan.backend.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.user.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService inbox")
class NotificationServiceTest {

    @Mock
    private UserNotificationRepository userNotificationRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
            userNotificationRepository,
            userAccountRepository
        );
    }

    @Test
    void upsertCreatesRowWhenMissing() {
        when(userNotificationRepository.findByUserIdAndTypeAndReferenceTypeAndReferenceId(
            10L, NotificationType.FAMILY_INVITE_UPDATE, "INVITATION", 5L))
            .thenReturn(Optional.empty());
        when(userNotificationRepository.saveAndFlush(any(UserNotification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.upsert(
            10L,
            NotificationType.FAMILY_INVITE_UPDATE,
            "INVITATION",
            5L,
            "Invite sent to jamie@example.com.",
            "Wong Family",
            null,
            null
        );

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationRepository).saveAndFlush(captor.capture());
        UserNotification row = captor.getValue();
        assertEquals(10L, row.getUserId());
        assertEquals(NotificationType.FAMILY_INVITE_UPDATE, row.getType());
        assertEquals("INVITATION", row.getReferenceType());
        assertEquals(5L, row.getReferenceId());
        assertEquals("Invite sent to jamie@example.com.", row.getTitle());
        assertNull(row.getReadAt());
    }

    @Test
    void listMineMapsExpiredFromExpiresAt() {
        when(userAccountRepository.existsById(30L)).thenReturn(true);
        UserNotification row = new UserNotification();
        row.setId(2L);
        row.setUserId(30L);
        row.setType(NotificationType.FAMILY_INVITE_REQUEST);
        row.setTitle("Join Wong Family?");
        row.setBody("Invited by Amelia.");
        row.setActionToken("tok");
        row.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        row.setUpdatedAt(Instant.parse("2026-08-14T00:00:00Z"));
        when(userNotificationRepository.findByUserIdOrderByUpdatedAtDesc(30L))
            .thenReturn(List.of(row));

        List<UserNotificationResponse> results = notificationService.listMine(30L);

        assertEquals(1, results.size());
        assertEquals("tok", results.get(0).actionToken());
        assertTrue(results.get(0).expired());
        assertFalse(results.get(0).read());
        assertEquals(NotificationType.FAMILY_INVITE_REQUEST, results.get(0).type());
    }

    @Test
    void deleteMineRemovesOwnedRow() {
        UserNotification row = new UserNotification();
        row.setId(9L);
        row.setUserId(10L);
        when(userNotificationRepository.findByIdAndUserId(9L, 10L)).thenReturn(Optional.of(row));

        notificationService.deleteMine(10L, 9L);

        verify(userNotificationRepository).delete(row);
    }

    @Test
    void deleteMineThrowsWhenMissing() {
        when(userNotificationRepository.findByIdAndUserId(9L, 10L)).thenReturn(Optional.empty());

        assertThrows(
            NotificationNotFoundException.class,
            () -> notificationService.deleteMine(10L, 9L)
        );
    }

    @Test
    void markAllReadDelegatesToRepository() {
        when(userAccountRepository.existsById(10L)).thenReturn(true);

        notificationService.markAllRead(10L);

        verify(userNotificationRepository).markAllRead(eq(10L), any(Instant.class));
    }
}
