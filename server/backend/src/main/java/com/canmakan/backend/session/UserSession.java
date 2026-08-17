package com.canmakan.backend.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One in-app session for an application user, tracked by client heartbeats.
 *
 * <p>The mobile client sends a heartbeat every ~30 seconds while the app is in the foreground. Each
 * heartbeat extends the current open session ({@code last_heartbeat_at}); a gap larger than the
 * session timeout (see {@code SessionTrackingService}) starts a new session. The session length is
 * {@code last_heartbeat_at - started_at}. This gives real "time spent in the app" instead of an
 * estimate from scan timing.
 *
 * @author XieHuayuan
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;
}
