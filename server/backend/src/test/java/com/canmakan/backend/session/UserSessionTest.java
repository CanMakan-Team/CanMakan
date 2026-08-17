package com.canmakan.backend.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link UserSession} entity accessors and both constructors.
 *
 * @author XieHuayuan
 */
@DisplayName("UC15: UserSession entity")
class UserSessionTest {

    @Test
    @DisplayName("all-args constructor exposes every field")
    void allArgsConstructorExposesFields() {
        LocalDateTime started = LocalDateTime.of(2026, 2, 1, 12, 0);
        LocalDateTime last = started.plusMinutes(5);

        UserSession session = new UserSession(1L, 7L, started, last);

        assertThat(session.getId()).isEqualTo(1L);
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getStartedAt()).isEqualTo(started);
        assertThat(session.getLastHeartbeatAt()).isEqualTo(last);
    }

    @Test
    @DisplayName("no-args constructor with setters exposes every field")
    void noArgsConstructorWithSetters() {
        LocalDateTime started = LocalDateTime.of(2026, 2, 1, 12, 0);
        LocalDateTime last = started.plusMinutes(10);

        UserSession session = new UserSession();
        session.setId(2L);
        session.setUserId(9L);
        session.setStartedAt(started);
        session.setLastHeartbeatAt(last);

        assertThat(session.getId()).isEqualTo(2L);
        assertThat(session.getUserId()).isEqualTo(9L);
        assertThat(session.getStartedAt()).isEqualTo(started);
        assertThat(session.getLastHeartbeatAt()).isEqualTo(last);
    }
}
