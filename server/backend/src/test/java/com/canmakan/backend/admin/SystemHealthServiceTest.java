package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.canmakan.backend.admin.dto.SystemHealthResponse;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the UC16 system-health aggregation over a deterministic dataset and fixed clock.
 *
 * @author XieHuayuan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC16: SystemHealthService aggregation")
class SystemHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    private SystemHealthRepository repository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private SystemHealthService service;

    @BeforeEach
    void setUp() throws SQLException {
        service = new SystemHealthService(repository, dataSource, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.isValid(anyInt())).thenReturn(true);
    }

    private static SystemHealthRepository.AiExecutionRow aiRow(long scanId, String tier, int latency) {
        return new SystemHealthRepository.AiExecutionRow() {
            public Long getScanId() {
                return scanId;
            }

            public String getExecutionTier() {
                return tier;
            }

            public Integer getLatencyMs() {
                return latency;
            }

            public Long getCreatedAtEpochMs() {
                return NOW.minus(30, ChronoUnit.MINUTES).toEpochMilli();
            }
        };
    }

    private static SystemHealthRepository.ScanQualityRow qualityRow(
            long total, long safe, long warning, long unsafe, long incomplete) {
        return new SystemHealthRepository.ScanQualityRow() {
            public Long getTotal() {
                return total;
            }

            public Long getSafe() {
                return safe;
            }

            public Long getWarning() {
                return warning;
            }

            public Long getUnsafe() {
                return unsafe;
            }

            public Long getIncomplete() {
                return incomplete;
            }
        };
    }

    private static SystemHealthRepository.AuditRow auditRow(String email, String action) {
        return new SystemHealthRepository.AuditRow() {
            public Long getTsEpochMs() {
                return NOW.toEpochMilli();
            }

            public String getAdminEmail() {
                return email;
            }

            public String getAction() {
                return action;
            }

            public String getTarget() {
                return "user 42";
            }

            public String getIpAddress() {
                return "203.0.113.9";
            }
        };
    }

    @Test
    @DisplayName("aggregates status, AI execution, audit trail and scan quality")
    void aggregatesSystemHealth() {
        when(repository.findAiExecutionRowsSince(any())).thenReturn(List.of(
                aiRow(1, "TIER_1_RULES", 100),
                aiRow(2, "TIER_1_RULES", 200),
                aiRow(3, "TIER_3_LLM", 900),
                aiRow(4, "TIER_3_LLM", 1500)));
        when(repository.findRecentAuditRows(anyInt())).thenReturn(List.of(
                auditRow("sysadmin@canmakan.com", "SUSPEND")));
        when(repository.findScanQualitySince(any())).thenReturn(qualityRow(10, 6, 3, 1, 2));

        SystemHealthResponse response = service.generate(24);

        assertThat(response.overallStatus()).isEqualTo("UP");
        assertThat(response.components()).extracting(SystemHealthResponse.ComponentHealth::name)
                .contains("db", "diskSpace", "application");

        assertThat(response.ai().totalCalls()).isEqualTo(4);
        assertThat(response.ai().tier3RatePct()).isEqualTo(50);
        assertThat(response.ai().averageLatencyMs()).isEqualTo(675);
        assertThat(response.ai().maxLatencyMs()).isEqualTo(1500);
        assertThat(response.ai().slowestCalls().get(0).latencyMs()).isEqualTo(1500);
        assertThat(response.ai().latencyTrend()).hasSize(12);

        assertThat(response.auditTrail()).hasSize(1);
        assertThat(response.auditTrail().get(0).admin()).isEqualTo("sysadmin@canmakan.com");

        assertThat(response.scanQuality().totalScans()).isEqualTo(10);
        assertThat(response.scanQuality().incompleteDataPct()).isEqualTo(20);
        assertThat(response.scanQuality().safePct()).isEqualTo(60);
        assertThat(response.scanQuality().warningPct()).isEqualTo(30);
        assertThat(response.scanQuality().unsafePct()).isEqualTo(10);
    }

    @Test
    @DisplayName("handles empty data without dividing by zero")
    void handlesEmptyData() {
        when(repository.findAiExecutionRowsSince(any())).thenReturn(List.of());
        when(repository.findRecentAuditRows(anyInt())).thenReturn(List.of());
        when(repository.findScanQualitySince(any())).thenReturn(qualityRow(0, 0, 0, 0, 0));

        SystemHealthResponse response = service.generate(24);

        assertThat(response.ai().totalCalls()).isZero();
        assertThat(response.ai().tier3RatePct()).isZero();
        assertThat(response.ai().averageLatencyMs()).isZero();
        assertThat(response.scanQuality().incompleteDataPct()).isZero();
        assertThat(response.scanQuality().totalScans()).isZero();
    }
}
