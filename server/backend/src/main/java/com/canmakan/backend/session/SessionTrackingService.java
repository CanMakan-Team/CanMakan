package com.canmakan.backend.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records in-app session activity from client heartbeats.
 *
 * <p>A heartbeat extends the user's current open session when it arrives within
 * {@link #SESSION_TIMEOUT} of the last one; otherwise a new session is started. The timeout is a few
 * times the client heartbeat interval so a single dropped ping (or a brief background) does not split
 * a session, while a real gap (app closed, crashed) correctly ends it without needing an explicit
 * "session end" event.
 *
 * @author XieHuayuan
 */
@Service
public class SessionTrackingService {

    /** A gap larger than this between heartbeats starts a new session. */
    static final Duration SESSION_TIMEOUT = Duration.ofSeconds(90);

    private final UserSessionRepository repository;
    private final Clock clock;

    @Autowired
    public SessionTrackingService(UserSessionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SessionTrackingService(UserSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void recordHeartbeat(Long userId) {
        if (userId == null) {
            return;
        }
        Instant now = clock.instant();
        UserSession current = repository.findFirstByUserIdOrderByLastHeartbeatAtDesc(userId).orElse(null);

        boolean extendsOpenSession = current != null
                && Duration.between(current.getLastHeartbeatAt(), now).compareTo(SESSION_TIMEOUT) <= 0;

        if (extendsOpenSession) {
            current.setLastHeartbeatAt(now);
            repository.save(current);
        } else {
            repository.save(new UserSession(null, userId, now, now));
        }
    }
}
