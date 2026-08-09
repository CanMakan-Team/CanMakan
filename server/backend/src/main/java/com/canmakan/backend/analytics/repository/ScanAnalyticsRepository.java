package com.canmakan.backend.analytics.repository;

import com.canmakan.backend.product.scan.Scan;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only access to scan data required by UC7 analytics. */
public interface ScanAnalyticsRepository extends Repository<Scan, Long> {

    @Query(value = """
            SELECT COUNT(*) AS totalScans,
                   COALESCE(SUM(CASE WHEN verdict = 'SAFE' THEN 1 ELSE 0 END), 0) AS safeCount,
                   COALESCE(SUM(CASE WHEN verdict = 'WARNING' THEN 1 ELSE 0 END), 0) AS warningCount,
                   COALESCE(SUM(CASE WHEN verdict = 'UNSAFE' THEN 1 ELSE 0 END), 0) AS unsafeCount
            FROM scans
            WHERE scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND verdict IN ('SAFE', 'WARNING', 'UNSAFE')
            """, nativeQuery = true)
    ScanSummaryProjection aggregateSummary(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query(value = """
            SELECT FLOOR(
                       (UNIX_TIMESTAMP(scanned_at) - :#{#start.epochSecond}) / 86400
                   ) AS dayOffset,
                   COUNT(*) AS totalCount,
                   COALESCE(SUM(CASE WHEN verdict = 'SAFE' THEN 1 ELSE 0 END), 0) AS safeCount,
                   COALESCE(SUM(CASE WHEN verdict = 'WARNING' THEN 1 ELSE 0 END), 0) AS warningCount,
                   COALESCE(SUM(CASE WHEN verdict = 'UNSAFE' THEN 1 ELSE 0 END), 0) AS unsafeCount
            FROM scans
            WHERE scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND verdict IN ('SAFE', 'WARNING', 'UNSAFE')
            GROUP BY FLOOR(
                         (UNIX_TIMESTAMP(scanned_at) - :#{#start.epochSecond}) / 86400
                     )
            ORDER BY FLOOR(
                         (UNIX_TIMESTAMP(scanned_at) - :#{#start.epochSecond}) / 86400
                     )
            """, nativeQuery = true)
    List<DailyScanTrendProjection> aggregateDailyTrend(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query(value = """
            SELECT id AS scanId,
                   findings_json AS findingsJson
            FROM scans
            WHERE scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND verdict IN ('SAFE', 'WARNING', 'UNSAFE')
            ORDER BY id
            """, nativeQuery = true)
    List<ScanFindingProjection> findFindingRows(
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
