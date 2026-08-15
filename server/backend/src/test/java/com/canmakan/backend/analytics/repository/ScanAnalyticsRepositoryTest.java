package com.canmakan.backend.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.analytics.Uc7IsolatedDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    Uc7IsolatedDatabase.DATASOURCE_URL_PROPERTY,
    Uc7IsolatedDatabase.DISABLE_AUTOMATIC_SQL_INIT_PROPERTY,
    Uc7IsolatedDatabase.DISABLE_HIBERNATE_DDL_PROPERTY
})
@Transactional
@ContextConfiguration(initializers = Uc7IsolatedDatabase.class)
@DisplayName("UC7: ScanAnalyticsRepository")
class ScanAnalyticsRepositoryTest {

    private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2030-01-03T00:00:00Z");

    @Autowired
    private ScanAnalyticsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private Long safeScanId;
    private Long warningScanId;
    private Long unsafeScanId;

    @BeforeEach
    void setUp() {
        Uc7IsolatedDatabase.assertConnectedToTestDatabase(dataSource);
        persistProduct("UC7_REPO_100", "Snack A", "Snacks");
        safeScanId = persistScan("UC7_REPO_100", "SAFE", "[]", START);
        warningScanId = persistScan(
                "UC7_REPO_100",
                "WARNING",
                "[{\"restrictionCode\":\"PEANUT\",\"ingredientName\":\"Peanut\",\"reason\":\"Match\"}]",
                START.plusSeconds(43_200)
        );
        unsafeScanId = persistScan(
                null,
                "UNSAFE",
                "[{\"restrictionCode\":\"DAIRY\",\"ingredientName\":\"Milk\",\"reason\":\"Match\"}]",
                END.minusSeconds(60)
        );
        persistScan("UC7_REPO_100", "SAFE", "[]", END);
        persistScan(null, "AVOID", "[]", START.plusSeconds(86_400));
    }

    @Test
    @DisplayName("summary includes start, excludes end, and counts only canonical verdicts")
    void aggregatesSummaryWithinHalfOpenRange() {
        ScanSummaryProjection summary = repository.aggregateSummary(START, END, null);

        assertThat(summary.getTotalScans()).isEqualTo(3);
        assertThat(summary.getSafeCount()).isEqualTo(1);
        assertThat(summary.getWarningCount()).isEqualTo(1);
        assertThat(summary.getUnsafeCount()).isEqualTo(1);
        assertThat(summary.getUniqueProducts()).isEqualTo(1);
    }

    @Test
    @DisplayName("daily trend groups canonical verdict counts by scan date")
    void aggregatesDailyTrendWithinHalfOpenRange() {
        List<DailyScanTrendProjection> trend = repository.aggregateDailyTrend(START, END, null);

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
        List<ScanFindingProjection> rows = repository.findFindingRows(START, END, null);

        assertThat(rows).extracting(ScanFindingProjection::getScanId)
                .containsExactly(safeScanId, warningScanId, unsafeScanId);
        assertThat(rows.get(0).getFindingsJson()).isEqualTo("[]");
        assertThat(rows.get(1).getFindingsJson()).contains("Peanut");
        assertThat(rows.get(2).getFindingsJson()).contains("Milk");
    }

    @Test
    @DisplayName("category filtering maps every unresolved category form to Uncategorised")
    void filtersAllAggregatesByNormalizedCategory() {
        persistProduct("UC7_REPO_000", "Legacy category", "0");
        persistProduct("UC7_REPO_NULL", "Null category", null);
        persistProduct("UC7_REPO_BLANK", "Blank category", "   ");
        Long legacyCategoryScanId = persistScan(
                "UC7_REPO_000",
                "SAFE",
                "[]",
                START.plusSeconds(60)
        );
        Long nullCategoryScanId = persistScan(
                "UC7_REPO_NULL",
                "WARNING",
                "[]",
                START.plusSeconds(120)
        );
        Long blankCategoryScanId = persistScan(
                "UC7_REPO_BLANK",
                "SAFE",
                "[]",
                START.plusSeconds(180)
        );
        Long missingProductScanId = persistOrphanedScan(
                "UC7_REPO_MISSING",
                "UNSAFE",
                "[]",
                START.plusSeconds(240)
        );
        ScanSummaryProjection summary = repository.aggregateSummary(
                START,
                END,
                "Uncategorised"
        );
        List<DailyScanTrendProjection> trend = repository.aggregateDailyTrend(
                START,
                END,
                "Uncategorised"
        );
        List<ScanFindingProjection> findings = repository.findFindingRows(
                START,
                END,
                "Uncategorised"
        );

        assertThat(summary.getTotalScans()).isEqualTo(5);
        assertThat(summary.getUniqueProducts()).isEqualTo(4);
        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).getDayOffset()).isZero();
        assertThat(trend.get(0).getTotalCount()).isEqualTo(4);
        assertThat(trend.get(0).getSafeCount()).isEqualTo(2);
        assertThat(trend.get(0).getWarningCount()).isEqualTo(1);
        assertThat(trend.get(0).getUnsafeCount()).isEqualTo(1);
        assertThat(trend.get(1).getDayOffset()).isEqualTo(1);
        assertThat(trend.get(1).getUnsafeCount()).isEqualTo(1);
        assertThat(findings).extracting(ScanFindingProjection::getScanId)
                .containsExactly(
                        unsafeScanId,
                        legacyCategoryScanId,
                        nullCategoryScanId,
                        blankCategoryScanId,
                        missingProductScanId
                );
    }

    @Test
    @DisplayName("product ranking excludes null barcodes and category overview includes them")
    void ranksProductsAndBuildsFullCategoryOverview() {
        List<ProductScanRankingProjection> products = repository.rankProducts(START, END, null);
        List<CategoryScanOverviewProjection> categories =
                repository.aggregateCategoryOverview(START, END);

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.getBarcode()).isEqualTo("UC7_REPO_100");
            assertThat(product.getProductName()).isEqualTo("Snack A");
            assertThat(product.getScanCount()).isEqualTo(2);
        });
        assertThat(categories).extracting(
                CategoryScanOverviewProjection::getCategory,
                CategoryScanOverviewProjection::getScanCount
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Snacks", 2L),
                org.assertj.core.groups.Tuple.tuple("Uncategorised", 1L)
        );
    }

    private void persistProduct(String barcode, String productName, String category) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO products (barcode, product_name, main_category_en) VALUES (?, ?, ?)",
                barcode,
                productName,
                category
        );
        assertThat(inserted).isOne();
    }

    private Long persistScan(
            String barcode,
            String verdict,
            String findingsJson,
            Instant scannedAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO scans (
                        user_id,
                        profile_id,
                        barcode,
                        verdict,
                        findings_json,
                        scanned_at
                    ) VALUES (4, 1, ?, ?, ?, FROM_UNIXTIME(?))
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, barcode);
            statement.setString(2, verdict);
            statement.setString(3, findingsJson);
            statement.setLong(4, scannedAt.getEpochSecond());
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long persistOrphanedScan(
            String barcode,
            String verdict,
            String findingsJson,
            Instant scannedAt
    ) {
        return jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (Statement foreignKeys = connection.createStatement()) {
                foreignKeys.execute("SET FOREIGN_KEY_CHECKS = 0");
            }
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO scans (
                            user_id,
                            profile_id,
                            barcode,
                            verdict,
                            findings_json,
                            scanned_at
                        ) VALUES (4, 1, ?, ?, ?, FROM_UNIXTIME(?))
                        """,
                        Statement.RETURN_GENERATED_KEYS
                )) {
                    statement.setString(1, barcode);
                    statement.setString(2, verdict);
                    statement.setString(3, findingsJson);
                    statement.setLong(4, scannedAt.getEpochSecond());
                    assertThat(statement.executeUpdate()).isOne();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        return keys.getLong(1);
                    }
                }
            } finally {
                try (Statement foreignKeys = connection.createStatement()) {
                    foreignKeys.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
        });
    }
}
