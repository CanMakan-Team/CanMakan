package com.canmakan.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.analytics.dto.ConsumerTrendsDataQuality;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.CategoryScanTrend;
import com.canmakan.backend.analytics.dto.DailyTrendPoint;
import com.canmakan.backend.analytics.dto.FlaggedIngredientTrend;
import com.canmakan.backend.analytics.dto.PeakScanDay;
import com.canmakan.backend.analytics.dto.ProductScanTrend;
import com.canmakan.backend.analytics.dto.RestrictionTrend;
import com.canmakan.backend.analytics.dto.TrendPeriod;
import com.canmakan.backend.analytics.dto.TrendSummary;
import com.canmakan.backend.analytics.exception.ConsumerTrendsValidationException;
import com.canmakan.backend.analytics.repository.DailyScanTrendProjection;
import com.canmakan.backend.analytics.repository.CategoryScanOverviewProjection;
import com.canmakan.backend.analytics.repository.ProductScanRankingProjection;
import com.canmakan.backend.analytics.repository.ScanAnalyticsRepository;
import com.canmakan.backend.analytics.repository.ScanFindingProjection;
import com.canmakan.backend.analytics.repository.ScanSummaryProjection;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC7: ConsumerTrendsService")
class ConsumerTrendsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T01:15:30Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

    @Mock
    private ScanAnalyticsRepository repository;

    private ObjectMapper objectMapper;
    private ConsumerTrendsService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ConsumerTrendsService(
                repository,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("defaults to the last 30 inclusive Singapore dates")
    void defaultsToThirtyInclusiveSingaporeDates() {
        stubValidAnalytics(summary(0, 0, 0, 0), List.of(), List.of());

        ConsumerTrendsResponse response = service.generateTrends(null, null, 5);

        assertThat(response.period()).isEqualTo(new TrendPeriod(
                LocalDate.of(2026, 7, 11),
                TODAY,
                "Asia/Singapore"
        ));
        assertThat(response.dailyTrend()).hasSize(30);
        assertThat(response.dailyTrend().getFirst().date()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(response.dailyTrend().getLast().date()).isEqualTo(TODAY);
        assertThat(response.generatedAt()).isEqualTo(
                OffsetDateTime.parse("2026-08-09T09:15:30+08:00")
        );

        Instant expectedStart = Instant.parse("2026-07-10T16:00:00Z");
        Instant expectedEnd = Instant.parse("2026-08-09T16:00:00Z");
        verify(repository).aggregateSummary(expectedStart, expectedEnd, null);
        verify(repository).aggregateDailyTrend(expectedStart, expectedEnd, null);
        verify(repository).findFindingRows(expectedStart, expectedEnd, null);
        verify(repository).rankProducts(expectedStart, expectedEnd, null);
        verify(repository).aggregateCategoryOverview(expectedStart, expectedEnd);
    }

    @Test
    @DisplayName("defaults a null ingredient limit to ten")
    void defaultsNullLimitToTen() {
        List<ScanFindingProjection> rows = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> findingRow(
                        (long) index,
                        findingsJson("ingredient " + String.format(Locale.ROOT, "%02d", index))
                ))
                .toList();
        stubValidAnalytics(
                summary(11, 0, 11, 0),
                List.of(daily(0, 11, 0, 11, 0)),
                rows
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, null);

        assertThat(response.topFlaggedIngredients()).hasSize(10);
        assertThat(response.topFlaggedIngredients())
                .extracting(FlaggedIngredientTrend::ingredientName)
                .containsExactly(
                        "ingredient 01",
                        "ingredient 02",
                        "ingredient 03",
                        "ingredient 04",
                        "ingredient 05",
                        "ingredient 06",
                        "ingredient 07",
                        "ingredient 08",
                        "ingredient 09",
                        "ingredient 10"
                );
    }

    @Test
    @DisplayName("rejects invalid date pairs, ranges, future dates, and limits")
    void rejectsInvalidRequestsBeforeQuerying() {
        LocalDate invertedEnd = TODAY.minusDays(1);
        LocalDate tooWideStart = TODAY.minusDays(90);
        LocalDate futureEnd = TODAY.plusDays(1);
        assertAll(
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(TODAY, null, 10)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(null, TODAY, 10)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(TODAY, invertedEnd, 10)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(tooWideStart, TODAY, 10)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(TODAY, futureEnd, 10)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(TODAY, TODAY, 0)
                ),
                () -> assertThrows(
                        ConsumerTrendsValidationException.class,
                        () -> service.generateTrends(TODAY, TODAY, 21)
                )
        );
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("converts Singapore midnight boundaries to UTC independently of JVM default zone")
    void convertsSingaporeBoundariesWithoutUsingJvmDefaultZone() {
        stubValidAnalytics(summary(0, 0, 0, 0), List.of(), List.of());
        TimeZone originalDefault = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            service.generateTrends(
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 1),
                    10
            );
        } finally {
            TimeZone.setDefault(originalDefault);
        }

        Instant expectedStart = Instant.parse("2026-07-31T16:00:00Z");
        Instant expectedExclusiveEnd = Instant.parse("2026-08-01T16:00:00Z");
        verify(repository).aggregateSummary(expectedStart, expectedExclusiveEnd, null);
        verify(repository).aggregateDailyTrend(expectedStart, expectedExclusiveEnd, null);
        verify(repository).findFindingRows(expectedStart, expectedExclusiveEnd, null);
    }

    @Test
    @DisplayName("returns a normal zero-filled response when no eligible scans exist")
    void returnsEmptyAggregateResponse() {
        stubValidAnalytics(summary(0, 0, 0, 0), List.of(), List.of());

        ConsumerTrendsResponse response = service.generateTrends(
                TODAY.minusDays(2),
                TODAY,
                10
        );

        assertThat(response.summary()).isEqualTo(new TrendSummary(
                0, 0, 0, 0, 0, new BigDecimal("0.00"), null
        ));
        assertThat(response.dailyTrend())
                .containsExactly(
                        new DailyTrendPoint(TODAY.minusDays(2), 0, 0, 0, 0),
                        new DailyTrendPoint(TODAY.minusDays(1), 0, 0, 0, 0),
                        new DailyTrendPoint(TODAY, 0, 0, 0, 0)
                );
        assertThat(response.topFlaggedIngredients()).isEmpty();
        assertThat(response.topRestrictions()).isEmpty();
        assertThat(response.mostScannedProducts()).isEmpty();
        assertThat(response.categoryOverview()).isEmpty();
        assertThat(response.dataQuality()).isEqualTo(new ConsumerTrendsDataQuality(false, 0));
    }

    @Test
    @DisplayName("zero-fills missing daily offsets and preserves ascending dates")
    void zeroFillsMissingDailyOffsets() {
        stubValidAnalytics(
                summary(3, 1, 1, 1),
                List.of(
                        daily(0, 2, 1, 1, 0),
                        daily(2, 1, 0, 0, 1)
                ),
                List.of()
        );

        ConsumerTrendsResponse response = service.generateTrends(
                TODAY.minusDays(2),
                TODAY,
                10
        );

        assertThat(response.dailyTrend()).containsExactly(
                new DailyTrendPoint(TODAY.minusDays(2), 2, 1, 1, 0),
                new DailyTrendPoint(TODAY.minusDays(1), 0, 0, 0, 0),
                new DailyTrendPoint(TODAY, 1, 0, 0, 1)
        );
    }

    @Test
    @DisplayName("calculates unique products, a two-decimal average, and the latest tied peak")
    void calculatesAddedSummaryMetrics() {
        stubValidAnalytics(
                summary(4, 2, 1, 1, 2),
                List.of(
                        daily(0, 2, 1, 1, 0),
                        daily(2, 2, 1, 0, 1)
                ),
                List.of()
        );

        ConsumerTrendsResponse response = service.generateTrends(
                TODAY.minusDays(2),
                TODAY,
                10
        );

        assertThat(response.summary().uniqueProducts()).isEqualTo(2);
        assertThat(response.summary().averageScansPerDay()).isEqualByComparingTo("1.33");
        assertThat(response.summary().peakScanDay()).isEqualTo(new PeakScanDay(TODAY, 2));
    }

    @Test
    @DisplayName("normalizes and applies a category without filtering the category overview")
    void appliesCategoryToFilteredAggregatesOnly() {
        stubValidAnalytics(
                summary(2, 1, 1, 0, 1),
                List.of(daily(0, 2, 1, 1, 0)),
                List.of(),
                List.of(product("200", "Snack", 2)),
                List.of(
                        category("Snacks and food", 2),
                        category("Uncategorised", 1),
                        category("Drinks", 1)
                )
        );

        ConsumerTrendsResponse response = service.generateTrends(
                TODAY,
                TODAY,
                10,
                "  Snacks   and food "
        );

        assertThat(response.appliedFilters().category()).isEqualTo("Snacks and food");
        assertThat(response.categoryOverview()).containsExactly(
                new CategoryScanTrend("Snacks and food", 2, new BigDecimal("50.00")),
                new CategoryScanTrend("Drinks", 1, new BigDecimal("25.00")),
                new CategoryScanTrend("Uncategorised", 1, new BigDecimal("25.00"))
        );
        verify(repository).aggregateSummary(any(), any(),
                org.mockito.ArgumentMatchers.eq("Snacks and food"));
        verify(repository).aggregateDailyTrend(any(), any(),
                org.mockito.ArgumentMatchers.eq("Snacks and food"));
        verify(repository).findFindingRows(any(), any(),
                org.mockito.ArgumentMatchers.eq("Snacks and food"));
        verify(repository).rankProducts(any(), any(),
                org.mockito.ArgumentMatchers.eq("Snacks and food"));
        verify(repository).aggregateCategoryOverview(any(), any());
    }

    @Test
    @DisplayName("maps a blank selected category to Uncategorised")
    void mapsBlankCategoryFilterToUncategorised() {
        stubValidAnalytics(summary(0, 0, 0, 0), List.of(), List.of());

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10, "   ");

        assertThat(response.appliedFilters().category()).isEqualTo("Uncategorised");
        verify(repository).aggregateSummary(any(), any(),
                org.mockito.ArgumentMatchers.eq("Uncategorised"));
    }

    @Test
    @DisplayName("rejects a category longer than the verified database field")
    void rejectsOversizedCategoryBeforeQuerying() {
        String oversizedCategory = "x".repeat(1001);
        assertThrows(
                ConsumerTrendsValidationException.class,
                () -> service.generateTrends(TODAY, TODAY, 10, oversizedCategory)
        );

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("ranks products by count then barcode and uses all filtered scans as denominator")
    void buildsDeterministicProductRanking() {
        stubValidAnalytics(
                summary(5, 2, 2, 1, 2),
                List.of(daily(0, 5, 2, 2, 1)),
                List.of(),
                List.of(
                        product("200", "Second barcode", 2),
                        product("100", "First barcode", 2)
                ),
                List.of()
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);

        assertThat(response.mostScannedProducts()).containsExactly(
                new ProductScanTrend(1, "First barcode", 2, new BigDecimal("40.00")),
                new ProductScanTrend(2, "Second barcode", 2, new BigDecimal("40.00"))
        );
    }

    @Test
    @DisplayName("counts each normalized restriction once per scan and excludes sentinels")
    void aggregatesCanonicalRestrictionCodes() {
        stubValidAnalytics(
                summary(3, 0, 3, 0),
                List.of(daily(0, 3, 0, 3, 0)),
                List.of(
                        findingRow(101L, findingsJson(List.of(
                                new Finding("PEANUT", "Peanut", "match"),
                                new Finding(" peanut ", "Peanut", "duplicate"),
                                new Finding("INCOMPLETE_DATA", "unknown", "quality")
                        ))),
                        findingRow(102L, findingsJson(List.of(
                                new Finding("dairy intolerance", "Milk", "match")
                        ))),
                        findingRow(103L, findingsJson(List.of(
                                new Finding("PEANUT", "Peanut", "match")
                        )))
                )
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 1);

        assertThat(response.topRestrictions()).containsExactly(
                new RestrictionTrend("PEANUT", 2),
                new RestrictionTrend("DAIRY_INTOLERANCE", 1)
        );
        assertThat(response.topFlaggedIngredients()).hasSize(1);
    }

    @Test
    @DisplayName("rejects an inconsistent summary breakdown")
    void rejectsInconsistentSummary() {
        ScanSummaryProjection inconsistentSummary = summary(2, 1, 0, 0);
        when(repository.aggregateSummary(any(), any(), nullable(String.class)))
                .thenReturn(inconsistentSummary);

        assertThrows(
                IllegalStateException.class,
                () -> service.generateTrends(TODAY, TODAY, 10)
        );
    }

    @Test
    @DisplayName("rejects an inconsistent daily breakdown")
    void rejectsInconsistentDailyBreakdown() {
        ScanSummaryProjection validSummary = summary(1, 0, 1, 0);
        DailyScanTrendProjection inconsistentDay = daily(0, 2, 0, 1, 0);
        when(repository.aggregateSummary(any(), any(), nullable(String.class)))
                .thenReturn(validSummary);
        when(repository.aggregateDailyTrend(any(), any(), nullable(String.class)))
                .thenReturn(List.of(inconsistentDay));

        assertThrows(
                IllegalStateException.class,
                () -> service.generateTrends(TODAY, TODAY, 10)
        );
    }

    @Test
    @DisplayName("rejects daily totals that do not match the summary")
    void rejectsDailyTotalMismatch() {
        ScanSummaryProjection validSummary = summary(2, 0, 2, 0);
        DailyScanTrendProjection incompleteDay = daily(0, 1, 0, 1, 0);
        when(repository.aggregateSummary(any(), any(), nullable(String.class)))
                .thenReturn(validSummary);
        when(repository.aggregateDailyTrend(any(), any(), nullable(String.class)))
                .thenReturn(List.of(incompleteDay));

        assertThrows(
                IllegalStateException.class,
                () -> service.generateTrends(TODAY, TODAY, 10)
        );
    }

    @Test
    @DisplayName("rejects duplicate daily offsets")
    void rejectsInvalidDailyOffsets() {
        ScanSummaryProjection validSummary = summary(2, 0, 2, 0);
        DailyScanTrendProjection firstDay = daily(0, 1, 0, 1, 0);
        DailyScanTrendProjection duplicateDay = mock(DailyScanTrendProjection.class);
        when(duplicateDay.getDayOffset()).thenReturn(0L);
        when(repository.aggregateSummary(any(), any(), nullable(String.class)))
                .thenReturn(validSummary);
        when(repository.aggregateDailyTrend(any(), any(), nullable(String.class))).thenReturn(List.of(
                firstDay,
                duplicateDay
        ));

        assertThrows(
                IllegalStateException.class,
                () -> service.generateTrends(TODAY, TODAY, 10)
        );
    }

    @ParameterizedTest(name = "rejects daily offset {0} for a one-day period")
    @ValueSource(longs = {-1, 1})
    void rejectsOutOfRangeDailyOffsets(long invalidOffset) {
        ScanSummaryProjection validSummary = summary(0, 0, 0, 0);
        DailyScanTrendProjection outOfRangeDay = mock(DailyScanTrendProjection.class);
        when(outOfRangeDay.getDayOffset()).thenReturn(invalidOffset);
        when(repository.aggregateSummary(any(), any(), nullable(String.class)))
                .thenReturn(validSummary);
        when(repository.aggregateDailyTrend(any(), any(), nullable(String.class)))
                .thenReturn(List.of(outOfRangeDay));

        assertThrows(
                IllegalStateException.class,
                () -> service.generateTrends(TODAY, TODAY, 10)
        );
    }

    @Test
    @DisplayName("parses canonical findings and counts an ingredient once per distinct scan")
    void aggregatesCanonicalFindingsPerDistinctScan() {
        stubValidAnalytics(
                summary(3, 0, 3, 0),
                List.of(daily(0, 3, 0, 3, 0)),
                List.of(
                        findingRow(101L, findingsJson(
                                "Peanut",
                                " peanut ",
                                "PEANUT",
                                " skimmed   MILK powder "
                        )),
                        findingRow(102L, findingsJson("peanut")),
                        findingRow(103L, findingsJson("PEANUT"))
                )
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);

        assertThat(response.topFlaggedIngredients()).containsExactly(
                new FlaggedIngredientTrend("PEANUT", 3),
                new FlaggedIngredientTrend("skimmed MILK powder", 1)
        );
        assertThat(response.dataQuality()).isEqualTo(new ConsumerTrendsDataQuality(false, 0));
    }

    @Test
    @DisplayName("preserves deterministic observed spelling without synthetic title case")
    void preservesObservedIngredientSpelling() {
        stubValidAnalytics(
                summary(1, 0, 1, 0),
                List.of(daily(0, 1, 0, 1, 0)),
                List.of(findingRow(101L, findingsJson(
                        "MSG",
                        "B12",
                        "omega-3",
                        "L-cysteine"
                )))
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);

        assertThat(response.topFlaggedIngredients()).containsExactly(
                new FlaggedIngredientTrend("B12", 1),
                new FlaggedIngredientTrend("L-cysteine", 1),
                new FlaggedIngredientTrend("MSG", 1),
                new FlaggedIngredientTrend("omega-3", 1)
        );
    }

    @Test
    @DisplayName("excludes blank and frozen sentinel subjects from ingredient ranking")
    void excludesSentinelIngredients() {
        stubValidAnalytics(
                summary(1, 0, 1, 0),
                List.of(daily(0, 1, 0, 1, 0)),
                List.of(findingRow(101L, findingsJson(
                        null,
                        "   ",
                        Finding.SUBJECT_UNKNOWN,
                        Finding.SUBJECT_LABEL,
                        Finding.SUBJECT_NUTRITION,
                        "incomplete",
                        " Missing   Data ",
                        "NOT AVAILABLE",
                        "N/A",
                        "NULL",
                        "Hazelnut"
                )))
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);

        assertThat(response.topFlaggedIngredients())
                .containsExactly(new FlaggedIngredientTrend("Hazelnut", 1));
    }

    @Test
    @DisplayName("marks malformed, legacy-shaped, and missing findings once per persisted row")
    void skipsMalformedFindingsWithoutRemovingScanCounts() {
        stubValidAnalytics(
                summary(4, 1, 2, 1),
                List.of(daily(0, 4, 1, 2, 1)),
                List.of(
                        findingRow(101L, "[not-json"),
                        findingRow(102L, "{\"matched_rules\":[\"PEANUT\"]}"),
                        findingRow(103L, null),
                        findingRow(104L, findingsJson("Milk"))
                )
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);

        assertThat(response.summary()).isEqualTo(new TrendSummary(
                4,
                1,
                2,
                1,
                0,
                new BigDecimal("4.00"),
                new PeakScanDay(TODAY, 4)
        ));
        assertThat(response.dailyTrend())
                .containsExactly(new DailyTrendPoint(TODAY, 4, 1, 2, 1));
        assertThat(response.topFlaggedIngredients())
                .containsExactly(new FlaggedIngredientTrend("Milk", 1));
        assertThat(response.dataQuality()).isEqualTo(new ConsumerTrendsDataQuality(true, 3));
    }

    @Test
    @DisplayName("sorts by count descending and display name ascending before applying limit")
    void sortsAndAppliesRequestedLimit() {
        stubValidAnalytics(
                summary(5, 0, 5, 0),
                List.of(daily(0, 5, 0, 5, 0)),
                List.of(
                        findingRow(101L, findingsJson("Banana", "Apple")),
                        findingRow(102L, findingsJson("banana")),
                        findingRow(103L, findingsJson("apple")),
                        findingRow(104L, findingsJson("Carrot")),
                        findingRow(105L, "[]")
                )
        );

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 2);

        assertThat(response.topFlaggedIngredients()).containsExactly(
                new FlaggedIngredientTrend("Apple", 2),
                new FlaggedIngredientTrend("Banana", 2)
        );
    }

    @Test
    @DisplayName("returns only aggregate DTO fields and no private scan data")
    void responseContractContainsOnlyAggregateInformation() {
        stubValidAnalytics(summary(0, 0, 0, 0), List.of(), List.of());

        ConsumerTrendsResponse response = service.generateTrends(TODAY, TODAY, 10);
        Set<String> exposedFields = Stream.of(
                        ConsumerTrendsResponse.class,
                        TrendPeriod.class,
                        TrendSummary.class,
                        DailyTrendPoint.class,
                        PeakScanDay.class,
                        ProductScanTrend.class,
                        CategoryScanTrend.class,
                        RestrictionTrend.class,
                        FlaggedIngredientTrend.class,
                        ConsumerTrendsDataQuality.class
                )
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(response).isNotNull();
        assertThat(exposedFields)
                .isNotEmpty()
                .doesNotContain(
                "scanId",
                "userId",
                "profileId",
                "barcode",
                "familyId",
                "email",
                "aiExplanation",
                "findingsJson",
                "findings"
        );
    }

    private void stubValidAnalytics(
            ScanSummaryProjection summary,
            List<DailyScanTrendProjection> daily,
            List<ScanFindingProjection> findings
    ) {
        stubValidAnalytics(summary, daily, findings, List.of(), List.of());
    }

    private void stubValidAnalytics(
            ScanSummaryProjection summary,
            List<DailyScanTrendProjection> daily,
            List<ScanFindingProjection> findings,
            List<ProductScanRankingProjection> products,
            List<CategoryScanOverviewProjection> categories
    ) {
        when(repository.aggregateSummary(any(), any(), nullable(String.class))).thenReturn(summary);
        when(repository.aggregateDailyTrend(any(), any(), nullable(String.class))).thenReturn(daily);
        when(repository.findFindingRows(any(), any(), nullable(String.class))).thenReturn(findings);
        when(repository.rankProducts(any(), any(), nullable(String.class))).thenReturn(products);
        when(repository.aggregateCategoryOverview(any(), any())).thenReturn(categories);
    }

    private static ScanSummaryProjection summary(
            long total,
            long safe,
            long warning,
            long unsafe
    ) {
        return summary(total, safe, warning, unsafe, 0);
    }

    private static ScanSummaryProjection summary(
            long total,
            long safe,
            long warning,
            long unsafe,
            long uniqueProducts
    ) {
        ScanSummaryProjection projection = mock(ScanSummaryProjection.class);
        when(projection.getTotalScans()).thenReturn(total);
        when(projection.getSafeCount()).thenReturn(safe);
        when(projection.getWarningCount()).thenReturn(warning);
        when(projection.getUnsafeCount()).thenReturn(unsafe);
        when(projection.getUniqueProducts()).thenReturn(uniqueProducts);
        return projection;
    }

    private static ProductScanRankingProjection product(
            String barcode,
            String productName,
            long scanCount
    ) {
        ProductScanRankingProjection projection = mock(ProductScanRankingProjection.class);
        when(projection.getBarcode()).thenReturn(barcode);
        when(projection.getProductName()).thenReturn(productName);
        when(projection.getScanCount()).thenReturn(scanCount);
        return projection;
    }

    private static CategoryScanOverviewProjection category(String name, long scanCount) {
        CategoryScanOverviewProjection projection = mock(CategoryScanOverviewProjection.class);
        when(projection.getCategory()).thenReturn(name);
        when(projection.getScanCount()).thenReturn(scanCount);
        return projection;
    }

    private static DailyScanTrendProjection daily(
            long offset,
            long total,
            long safe,
            long warning,
            long unsafe
    ) {
        DailyScanTrendProjection projection = mock(DailyScanTrendProjection.class);
        when(projection.getDayOffset()).thenReturn(offset);
        when(projection.getTotalCount()).thenReturn(total);
        when(projection.getSafeCount()).thenReturn(safe);
        when(projection.getWarningCount()).thenReturn(warning);
        when(projection.getUnsafeCount()).thenReturn(unsafe);
        return projection;
    }

    private static ScanFindingProjection findingRow(Long scanId, String findingsJson) {
        ScanFindingProjection projection = mock(ScanFindingProjection.class);
        when(projection.getScanId()).thenReturn(scanId);
        when(projection.getFindingsJson()).thenReturn(findingsJson);
        return projection;
    }

    private String findingsJson(String... ingredientNames) {
        List<Finding> findings = Arrays.stream(ingredientNames)
                .map(name -> new Finding("TEST", name, "test reason"))
                .toList();
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Test findings could not be serialized", exception);
        }
    }

    private String findingsJson(List<Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Test findings could not be serialized", exception);
        }
    }
}
