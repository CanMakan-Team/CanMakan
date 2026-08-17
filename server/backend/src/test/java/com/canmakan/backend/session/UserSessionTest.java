package com.canmakan.backend.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
        Instant started = Instant.parse("2026-02-01T12:00:00Z");
        Instant last = started.plusSeconds(300);

        UserSession session = new UserSession(1L, 7L, started, last);

        assertThat(session.getId()).isEqualTo(1L);
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getStartedAt()).isEqualTo(started);
        assertThat(session.getLastHeartbeatAt()).isEqualTo(last);
    }

    @Test
    @DisplayName("no-args constructor with setters exposes every field")
    void noArgsConstructorWithSetters() {
        Instant started = Instant.parse("2026-02-01T12:00:00Z");
        Instant last = started.plusSeconds(600);

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
