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
                   COALESCE(SUM(CASE WHEN s.verdict = 'SAFE' THEN 1 ELSE 0 END), 0) AS safeCount,
                   COALESCE(SUM(CASE WHEN s.verdict = 'WARNING' THEN 1 ELSE 0 END), 0) AS warningCount,
                   COALESCE(SUM(CASE WHEN s.verdict = 'UNSAFE' THEN 1 ELSE 0 END), 0) AS unsafeCount,
                   COUNT(DISTINCT s.barcode) AS uniqueProducts
            FROM scans s
            LEFT JOIN products p ON p.barcode = s.barcode
            WHERE s.scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND s.scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND s.verdict IN ('SAFE', 'WARNING', 'UNSAFE')
              AND (
                    :#{#category} IS NULL
                    OR LOWER(CASE
                        WHEN s.barcode IS NULL
                          OR p.barcode IS NULL
                          OR p.main_category_en IS NULL
                          OR TRIM(p.main_category_en) = ''
                          OR TRIM(p.main_category_en) = '0'
                        THEN 'Uncategorised'
                        ELSE TRIM(p.main_category_en)
                    END) = LOWER(:#{#category})
              )
            """, nativeQuery = true)
    ScanSummaryProjection aggregateSummary(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("category") String category
    );

    @Query(value = """
            SELECT FLOOR(
                       (UNIX_TIMESTAMP(s.scanned_at) - :#{#start.epochSecond}) / 86400
                   ) AS dayOffset,
                   COUNT(*) AS totalCount,
                   COALESCE(SUM(CASE WHEN s.verdict = 'SAFE' THEN 1 ELSE 0 END), 0) AS safeCount,
                   COALESCE(SUM(CASE WHEN s.verdict = 'WARNING' THEN 1 ELSE 0 END), 0) AS warningCount,
                   COALESCE(SUM(CASE WHEN s.verdict = 'UNSAFE' THEN 1 ELSE 0 END), 0) AS unsafeCount
            FROM scans s
            LEFT JOIN products p ON p.barcode = s.barcode
            WHERE s.scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND s.scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND s.verdict IN ('SAFE', 'WARNING', 'UNSAFE')
              AND (
                    :#{#category} IS NULL
                    OR LOWER(CASE
                        WHEN s.barcode IS NULL
                          OR p.barcode IS NULL
                          OR p.main_category_en IS NULL
                          OR TRIM(p.main_category_en) = ''
                          OR TRIM(p.main_category_en) = '0'
                        THEN 'Uncategorised'
                        ELSE TRIM(p.main_category_en)
                    END) = LOWER(:#{#category})
              )
            GROUP BY FLOOR(
                         (UNIX_TIMESTAMP(s.scanned_at) - :#{#start.epochSecond}) / 86400
                     )
            ORDER BY FLOOR(
                         (UNIX_TIMESTAMP(s.scanned_at) - :#{#start.epochSecond}) / 86400
                     )
            """, nativeQuery = true)
    List<DailyScanTrendProjection> aggregateDailyTrend(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("category") String category
    );

    @Query(value = """
            SELECT s.id AS scanId,
                   s.findings_json AS findingsJson
            FROM scans s
            LEFT JOIN products p ON p.barcode = s.barcode
            WHERE s.scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND s.scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND s.verdict IN ('SAFE', 'WARNING', 'UNSAFE')
              AND (
                    :#{#category} IS NULL
                    OR LOWER(CASE
                        WHEN s.barcode IS NULL
                          OR p.barcode IS NULL
                          OR p.main_category_en IS NULL
                          OR TRIM(p.main_category_en) = ''
                          OR TRIM(p.main_category_en) = '0'
                        THEN 'Uncategorised'
                        ELSE TRIM(p.main_category_en)
                    END) = LOWER(:#{#category})
              )
            ORDER BY s.id
            """, nativeQuery = true)
    List<ScanFindingProjection> findFindingRows(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("category") String category
    );

    @Query(value = """
            SELECT s.barcode AS barcode,
                   COALESCE(NULLIF(TRIM(p.product_name), ''), 'Unknown product') AS productName,
                   COUNT(*) AS scanCount
            FROM scans s
            LEFT JOIN products p ON p.barcode = s.barcode
            WHERE s.scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND s.scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND s.verdict IN ('SAFE', 'WARNING', 'UNSAFE')
              AND s.barcode IS NOT NULL
              AND (
                    :#{#category} IS NULL
                    OR LOWER(CASE
                        WHEN p.barcode IS NULL
                          OR p.main_category_en IS NULL
                          OR TRIM(p.main_category_en) = ''
                          OR TRIM(p.main_category_en) = '0'
                        THEN 'Uncategorised'
                        ELSE TRIM(p.main_category_en)
                    END) = LOWER(:#{#category})
              )
            GROUP BY s.barcode, COALESCE(NULLIF(TRIM(p.product_name), ''), 'Unknown product')
            ORDER BY COUNT(*) DESC, s.barcode ASC
            LIMIT 20
            """, nativeQuery = true)
    List<ProductScanRankingProjection> rankProducts(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("category") String category
    );

    @Query(value = """
            SELECT CASE
                       WHEN s.barcode IS NULL
                         OR p.barcode IS NULL
                         OR p.main_category_en IS NULL
                         OR TRIM(p.main_category_en) = ''
                         OR TRIM(p.main_category_en) = '0'
                       THEN 'Uncategorised'
                       ELSE TRIM(p.main_category_en)
                   END AS category,
                   COUNT(*) AS scanCount
            FROM scans s
            LEFT JOIN products p ON p.barcode = s.barcode
            WHERE s.scanned_at >= FROM_UNIXTIME(:#{#start.epochSecond})
              AND s.scanned_at < FROM_UNIXTIME(:#{#end.epochSecond})
              AND s.verdict IN ('SAFE', 'WARNING', 'UNSAFE')
            GROUP BY CASE
                         WHEN s.barcode IS NULL
                           OR p.barcode IS NULL
                           OR p.main_category_en IS NULL
                           OR TRIM(p.main_category_en) = ''
                           OR TRIM(p.main_category_en) = '0'
                         THEN 'Uncategorised'
                         ELSE TRIM(p.main_category_en)
                     END
            ORDER BY COUNT(*) DESC, category ASC
            """, nativeQuery = true)
    List<CategoryScanOverviewProjection> aggregateCategoryOverview(
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
