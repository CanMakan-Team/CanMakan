package com.canmakan.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.analytics.dto.ConsumerTrendsDataQuality;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.DailyTrendPoint;
import com.canmakan.backend.analytics.dto.TrendSummary;
import com.canmakan.backend.analytics.repository.ScanAnalyticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("UC7: ConsumerTrendsService MySQL boundaries")
class ConsumerTrendsServiceIntegrationTest {

    private static final LocalDate REPORTING_DATE = LocalDate.of(2030, 8, 1);
    private static final Instant START = Instant.parse("2030-07-31T16:00:00Z");
    private static final Instant END = Instant.parse("2030-08-01T16:00:00Z");

    @Autowired
    private ScanAnalyticsRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("uses Singapore midnight instants through the real JDBC and MySQL path")
    void appliesSingaporeCalendarBoundariesIndependentOfJvmDefaultZone() {
        TimeZone originalDefault = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            insertScanAtInstant("UC7_TZ_BEFORE", "SAFE", START.minusSeconds(1));
            insertScanAtInstant("UC7_TZ_START", "WARNING", START);
            insertScanAtInstant("UC7_TZ_LATE", "UNSAFE", END.minusSeconds(1));
            insertScanAtInstant("UC7_TZ_END", "SAFE", END);

            ConsumerTrendsService service = new ConsumerTrendsService(
                    repository,
                    objectMapper,
                    Clock.fixed(Instant.parse("2030-08-02T00:00:00Z"), ZoneOffset.UTC)
            );

            ConsumerTrendsResponse response = service.generateTrends(
                    REPORTING_DATE,
                    REPORTING_DATE,
                    10
            );

            assertThat(response.summary()).isEqualTo(new TrendSummary(2, 0, 1, 1));
            assertThat(response.dailyTrend()).containsExactly(
                    new DailyTrendPoint(REPORTING_DATE, 2, 0, 1, 1)
            );
            assertThat(response.topFlaggedIngredients()).isEmpty();
            assertThat(response.dataQuality()).isEqualTo(new ConsumerTrendsDataQuality(false, 0));
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    private void insertScanAtInstant(String marker, String verdict, Instant scannedAt) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO scans (
                    user_id,
                    profile_id,
                    verdict,
                    ai_explanation,
                    findings_json,
                    scanned_at
                ) VALUES (4, 1, ?, ?, '[]', FROM_UNIXTIME(?))
                """,
                verdict,
                marker,
                scannedAt.getEpochSecond()
        );

        assertThat(inserted).isOne();
        Long storedEpoch = jdbcTemplate.queryForObject(
                "SELECT UNIX_TIMESTAMP(scanned_at) FROM scans WHERE ai_explanation = ?",
                Long.class,
                marker
        );
        assertThat(storedEpoch).isEqualTo(scannedAt.getEpochSecond());
    }
}
