package com.canmakan.backend.analytics.repository;

import com.canmakan.backend.product.scan.Scan;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Read-only access to the raw application-user and scan data UC15 needs. The heavy lifting
 * (activity windows, retention cohorts, sessions, heatmap) is done in {@code UsageStatisticsService}
 * over these two small result sets, which keeps the SQL simple and the aggregation logic unit-testable.
 *
 * <p>Only accounts with the {@code USER} role are included; system admins are excluded from usage
 * statistics about app users.
 */
public interface UsageStatisticsRepository extends Repository<Scan, Long> {

    @Query(value = """
            SELECT u.id AS userId,
                   UNIX_TIMESTAMP(u.created_at) * 1000 AS createdAtEpochMs,
                   (SELECT COUNT(*) FROM dietary_profiles dp WHERE dp.linked_user_id = u.id) AS profileCount
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE r.name = 'USER'
            """, nativeQuery = true)
    List<AppUserProjection> findAppUsers();

    @Query(value = """
            SELECT s.user_id AS userId,
                   UNIX_TIMESTAMP(s.scanned_at) * 1000 AS scannedAtEpochMs
            FROM scans s
            JOIN users u ON u.id = s.user_id
            JOIN roles r ON r.id = u.role_id
            WHERE r.name = 'USER'
            """, nativeQuery = true)
    List<UserScanInstantProjection> findAppUserScans();
}
