package com.canmakan.backend.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("UC7: ScanAnalyticsRepository")
class ScanAnalyticsRepositoryTest {

    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2030-01-03T00:00:00Z");

    @Autowired
    private ScanAnalyticsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long safeScanId;
    private Long warningScanId;
    private Long unsafeScanId;

    @BeforeEach
    void setUp() {
        safeScanId = persistScan("SAFE", "[]", START);
        warningScanId = persistScan(
                "WARNING",
                "[{\"restrictionCode\":\"PEANUT\",\"ingredientName\":\"Peanut\",\"reason\":\"Match\"}]",
                START.plusSeconds(43_200)
        );
        unsafeScanId = persistScan(
                "UNSAFE",
                "[{\"restrictionCode\":\"DAIRY\",\"ingredientName\":\"Milk\",\"reason\":\"Match\"}]",
                END.minusSeconds(60)
        );
        persistScan("SAFE", "[]", END);
        persistScan("AVOID", "[]", START.plusSeconds(86_400));
    }

    @Test
    @DisplayName("summary includes start, excludes end, and counts only canonical verdicts")
    void aggregatesSummaryWithinHalfOpenRange() {
        ScanSummaryProjection summary = repository.aggregateSummary(START, END);

        assertThat(summary.getTotalScans()).isEqualTo(3);
        assertThat(summary.getSafeCount()).isEqualTo(1);
        assertThat(summary.getWarningCount()).isEqualTo(1);
        assertThat(summary.getUnsafeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("daily trend groups canonical verdict counts by scan date")
    void aggregatesDailyTrendWithinHalfOpenRange() {
        List<DailyScanTrendProjection> trend = repository.aggregateDailyTrend(START, END);

        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).getDayOffset()).isZero();
        assertThat(trend.get(0).getTotalCount()).isEqualTo(2);
        assertThat(trend.get(0).getSafeCount()).isEqualTo(1);
        assertThat(trend.get(0).getWarningCount()).isEqualTo(1);
        assertThat(trend.get(0).getUnsafeCount()).isZero();

        assertThat(trend.get(1).getDayOffset()).isEqualTo(1);
        assertThat(trend.get(1).getTotalCount()).isEqualTo(1);
        assertThat(trend.get(1).getSafeCount()).isZero();
        assertThat(trend.get(1).getWarningCount()).isZero();
        assertThat(trend.get(1).getUnsafeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("finding rows contain only scan identity and persisted JSON for scans in range")
    void returnsFindingRowsWithinHalfOpenRange() {
        List<ScanFindingProjection> rows = repository.findFindingRows(START, END);

        assertThat(rows).extracting(ScanFindingProjection::getScanId)
                .containsExactly(safeScanId, warningScanId, unsafeScanId);
        assertThat(rows.get(0).getFindingsJson()).isEqualTo("[]");
        assertThat(rows.get(1).getFindingsJson()).contains("Peanut");
        assertThat(rows.get(2).getFindingsJson()).contains("Milk");
    }

    private Long persistScan(String verdict, String findingsJson, Instant scannedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO scans (
                        user_id,
                        profile_id,
                        verdict,
                        findings_json,
                        scanned_at
                    ) VALUES (4, 1, ?, ?, FROM_UNIXTIME(?))
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, verdict);
            statement.setString(2, findingsJson);
            statement.setLong(3, scannedAt.getEpochSecond());
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
