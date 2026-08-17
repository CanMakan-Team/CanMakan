package com.canmakan.backend.session;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link UserSession} plus the aggregates UC15 engagement metrics need.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /** The user's most recently touched session, used to decide whether a heartbeat extends it. */
    Optional<UserSession> findFirstByUserIdOrderByLastHeartbeatAtDesc(Long userId);

    /** Average session length, session count, active users and active user-days within the window. */
    interface SessionAggregate {
        Double getAvgSeconds();

        Long getTotalSessions();

        Long getActiveUsers();

        Long getActiveUserDays();
    }

    @Query(value = """
            SELECT AVG(TIMESTAMPDIFF(SECOND, started_at, last_heartbeat_at)) AS avgSeconds,
                   COUNT(*) AS totalSessions,
                   COUNT(DISTINCT user_id) AS activeUsers,
                   COUNT(DISTINCT user_id, DATE(started_at)) AS activeUserDays
            FROM user_sessions
            WHERE started_at >= FROM_UNIXTIME(:#{#since.epochSecond})
            """, nativeQuery = true)
    SessionAggregate aggregateSince(@Param("since") Instant since);
}
