package com.canmakan.backend.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for heartbeat session bucketing over a fixed clock.
 *
 * @author XieHuayuan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC15: SessionTrackingService heartbeats")
class SessionTrackingServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final long USER_ID = 7L;

    @Mock
    private UserSessionRepository repository;

    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;

    private SessionTrackingService service;
    private LocalDateTime nowLocal;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SessionTrackingService(repository, clock);
        nowLocal = LocalDateTime.now(clock);
    }

    @Test
    @DisplayName("first heartbeat starts a new session with started == last heartbeat")
    void firstHeartbeatStartsSession() {
        when(repository.findFirstByUserIdOrderByLastHeartbeatAtDesc(USER_ID)).thenReturn(Optional.empty());

        service.recordHeartbeat(USER_ID);

        verify(repository).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStartedAt()).isEqualTo(nowLocal);
        assertThat(saved.getLastHeartbeatAt()).isEqualTo(nowLocal);
    }

    @Test
    @DisplayName("a heartbeat within the timeout extends the open session")
    void heartbeatWithinTimeoutExtendsSession() {
        LocalDateTime startedAt = nowLocal.minusMinutes(10);
        UserSession open = new UserSession(1L, USER_ID, startedAt, nowLocal.minusSeconds(60));
        when(repository.findFirstByUserIdOrderByLastHeartbeatAtDesc(USER_ID)).thenReturn(Optional.of(open));

        service.recordHeartbeat(USER_ID);

        verify(repository).save(open);
        assertThat(open.getStartedAt()).isEqualTo(startedAt);
        assertThat(open.getLastHeartbeatAt()).isEqualTo(nowLocal);
    }

    @Test
    @DisplayName("a null user id is ignored and touches no session state")
    void nullUserIdIsIgnored() {
        service.recordHeartbeat(null);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("the autowired constructor uses the system clock and records a first heartbeat")
    void autowiredConstructorRecordsFirstHeartbeat() {
        SessionTrackingService systemClockService = new SessionTrackingService(repository);
        when(repository.findFirstByUserIdOrderByLastHeartbeatAtDesc(USER_ID)).thenReturn(Optional.empty());

        systemClockService.recordHeartbeat(USER_ID);

        verify(repository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("a heartbeat after the timeout starts a new session")
    void heartbeatAfterTimeoutStartsNewSession() {
        UserSession stale = new UserSession(1L, USER_ID, nowLocal.minusMinutes(30), nowLocal.minusSeconds(120));
        when(repository.findFirstByUserIdOrderByLastHeartbeatAtDesc(USER_ID)).thenReturn(Optional.of(stale));

        service.recordHeartbeat(USER_ID);

        verify(repository).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getStartedAt()).isEqualTo(nowLocal);
        assertThat(saved.getLastHeartbeatAt()).isEqualTo(nowLocal);
    }
}
