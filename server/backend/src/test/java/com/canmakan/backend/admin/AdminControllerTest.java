package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.analytics.dto.ConsumerTrendsDataQuality;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.CategoryScanTrend;
import com.canmakan.backend.analytics.dto.ConsumerTrendsAppliedFilters;
import com.canmakan.backend.analytics.dto.DailyTrendPoint;
import com.canmakan.backend.analytics.dto.FlaggedIngredientTrend;
import com.canmakan.backend.analytics.dto.PeakScanDay;
import com.canmakan.backend.analytics.dto.ProductScanTrend;
import com.canmakan.backend.analytics.dto.RestrictionTrend;
import com.canmakan.backend.analytics.dto.TrendPeriod;
import com.canmakan.backend.analytics.dto.TrendSummary;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse;
import com.canmakan.backend.analytics.exception.ConsumerTrendsValidationException;
import com.canmakan.backend.analytics.service.ConsumerTrendsService;
import com.canmakan.backend.analytics.service.UsageStatisticsService;
import jakarta.servlet.ServletException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("UC7: GET /api/admin/consumer-trends HTTP contract")
class AdminControllerTest {

    private static final String ENDPOINT = "/api/admin/consumer-trends";
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 7);

    private MockMvc mockMvc;
    private ConsumerTrendsService consumerTrendsService;
    private UserAccountManagementService userAccountManagementService;
    private UsageStatisticsService usageStatisticsService;

    @BeforeEach
    void setUp() {
        consumerTrendsService = mock(ConsumerTrendsService.class);
        userAccountManagementService = mock(UserAccountManagementService.class);
        usageStatisticsService = mock(UsageStatisticsService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminController(
                        consumerTrendsService,
                        userAccountManagementService,
                        usageStatisticsService
                ))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("usage statistics defaults to a 7-day period when request param is omitted")
    void usageStatisticsUsesDefaultPeriod() throws Exception {
        UsageStatisticsResponse payload = new UsageStatisticsResponse(
                7,
                "2026-08-16T10:00:00Z",
                new UsageStatisticsResponse.Kpis(1, 2, 3, 4),
                new UsageStatisticsResponse.Acquisition(List.of(), List.of()),
                new UsageStatisticsResponse.Activity(2, 3, 4, 50, 40, 60),
                new UsageStatisticsResponse.Retention(1, 2, 3, 4, 5, 6, 7),
                new UsageStatisticsResponse.Engagement(4, 2.3, 1.4, List.of())
        );
        when(usageStatisticsService.generate(7)).thenReturn(payload);

        mockMvc.perform(get("/api/admin/usage-statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.kpis.newSignups").value(1));

        verify(usageStatisticsService).generate(7);
    }

    @Test
    @DisplayName("usage statistics forwards an explicit periodDays query parameter")
    void usageStatisticsForwardsExplicitPeriod() throws Exception {
        UsageStatisticsResponse payload = new UsageStatisticsResponse(
                30,
                "2026-08-16T10:00:00Z",
                new UsageStatisticsResponse.Kpis(12, 22, 32, 42),
                new UsageStatisticsResponse.Acquisition(List.of(), List.of()),
                new UsageStatisticsResponse.Activity(22, 32, 42, 50, 40, 60),
                new UsageStatisticsResponse.Retention(10, 20, 30, 40, 50, 60, 70),
                new UsageStatisticsResponse.Engagement(42, 2.7, 3.4, List.of())
        );
        when(usageStatisticsService.generate(30)).thenReturn(payload);

        mockMvc.perform(get("/api/admin/usage-statistics").param("periodDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.kpis.dailyActiveUsers").value(22));

        verify(usageStatisticsService).generate(30);
    }

    @Test
    @DisplayName("explicit parameters bind and return the frozen aggregate JSON contract")
    void explicitRequestReturnsAggregateContract() throws Exception {
        when(consumerTrendsService.generateTrends(FROM, TO, 5, "Snacks"))
                .thenReturn(representativeResponse());

        mockMvc.perform(get(ENDPOINT)
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-07")
                        .param("limit", "5")
                        .param("category", "Snacks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.period.from").value("2026-08-01"))
                .andExpect(jsonPath("$.period.to").value("2026-08-07"))
                .andExpect(jsonPath("$.period.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.appliedFilters.category").value("Snacks"))
                .andExpect(jsonPath("$.summary.totalScans").value(3))
                .andExpect(jsonPath("$.summary.safeCount").value(1))
                .andExpect(jsonPath("$.summary.warningCount").value(1))
                .andExpect(jsonPath("$.summary.unsafeCount").value(1))
                .andExpect(jsonPath("$.summary.uniqueProducts").value(2))
                .andExpect(jsonPath("$.summary.averageScansPerDay").value(0.43))
                .andExpect(jsonPath("$.summary.peakScanDay.date").value("2026-08-01"))
                .andExpect(jsonPath("$.summary.peakScanDay.scanCount").value(3))
                .andExpect(jsonPath("$.dailyTrend[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.dailyTrend[0].totalCount").value(3))
                .andExpect(jsonPath("$.dailyTrend[0].safeCount").value(1))
                .andExpect(jsonPath("$.dailyTrend[0].warningCount").value(1))
                .andExpect(jsonPath("$.dailyTrend[0].unsafeCount").value(1))
                .andExpect(jsonPath("$.mostScannedProducts[0].rank").value(1))
                .andExpect(jsonPath("$.mostScannedProducts[0].productName").value("Snack A"))
                .andExpect(jsonPath("$.mostScannedProducts[0].scanCount").value(2))
                .andExpect(jsonPath("$.mostScannedProducts[0].percentage").value(66.67))
                .andExpect(jsonPath("$.categoryOverview[0].category").value("Snacks"))
                .andExpect(jsonPath("$.categoryOverview[0].scanCount").value(2))
                .andExpect(jsonPath("$.categoryOverview[0].percentage").value(66.67))
                .andExpect(jsonPath("$.topRestrictions[0].restrictionCode").value("PEANUT"))
                .andExpect(jsonPath("$.topRestrictions[0].flaggedCount").value(2))
                .andExpect(jsonPath("$.topFlaggedIngredients[0].ingredientName").value("MSG"))
                .andExpect(jsonPath("$.topFlaggedIngredients[0].flaggedCount").value(2))
                .andExpect(jsonPath("$.dataQuality.partial").value(false))
                .andExpect(jsonPath("$.dataQuality.skippedMalformedFindings").value(0))
                .andExpect(jsonPath("$.generatedAt").value("2026-08-08T09:15:30+08:00"))
                .andExpect(jsonPath("$..scanId").doesNotExist())
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..profileId").doesNotExist())
                .andExpect(jsonPath("$..barcode").doesNotExist())
                .andExpect(jsonPath("$..aiExplanation").doesNotExist())
                .andExpect(jsonPath("$..findingsJson").doesNotExist());

        verify(consumerTrendsService).generateTrends(FROM, TO, 5, "Snacks");
    }

    @Test
    @DisplayName("omitted parameters remain null for service-owned defaults")
    void defaultRequestDelegatesNullParameters() throws Exception {
        when(consumerTrendsService.generateTrends(null, null, null, null))
                .thenReturn(representativeResponse());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk());

        verify(consumerTrendsService).generateTrends(null, null, null, null);
    }

    @ParameterizedTest(name = "one-sided {0} parameter returns 400")
    @ValueSource(strings = {"from", "to"})
    void oneSidedDateReturnsBadRequest(String suppliedParameter) throws Exception {
        LocalDate from = "from".equals(suppliedParameter) ? FROM : null;
        LocalDate to = "to".equals(suppliedParameter) ? TO : null;
        String suppliedValue = "from".equals(suppliedParameter)
                ? FROM.toString()
                : TO.toString();
        stubValidationFailure(from, to, null);

        mockMvc.perform(get(ENDPOINT).param(suppliedParameter, suppliedValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("reversed range validation returns 400")
    void reversedRangeReturnsBadRequest() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);
        stubValidationFailure(from, to, null);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("inclusive period longer than 90 days returns 400")
    void oversizedPeriodReturnsBadRequest() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 1);
        stubValidationFailure(from, to, null);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("future-date validation returns 400")
    void futureDateReturnsBadRequest() throws Exception {
        LocalDate futureDate = LocalDate.of(2999, 1, 1);
        stubValidationFailure(futureDate, futureDate, null);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", futureDate.toString())
                        .param("to", futureDate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest(name = "limit {0} validation returns 400")
    @ValueSource(ints = {0, 21})
    void invalidLimitReturnsBadRequest(int limit) throws Exception {
        stubValidationFailure(FROM, TO, limit);

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("invalid category validation returns 400")
    void invalidCategoryReturnsBadRequest() throws Exception {
        String category = "x".repeat(1001);
        when(consumerTrendsService.generateTrends(FROM, TO, null, category))
                .thenThrow(new ConsumerTrendsValidationException("Invalid trend criteria."));

        mockMvc.perform(get(ENDPOINT)
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("category", category))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("malformed ISO date is rejected by Spring binding")
    void malformedDateReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("from", "2026-99-99")
                        .param("to", "2026-08-07"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consumerTrendsService);
    }

    @Test
    @DisplayName("malformed integer is rejected by Spring binding")
    void malformedLimitReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("limit", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consumerTrendsService);
    }

    @Test
    @DisplayName("internal analytics failure is not translated to 400")
    void internalFailureIsNotMappedToBadRequest() {
        IllegalStateException internalFailure = new IllegalStateException(
                "Consumer trend analytics are inconsistent"
        );
        when(consumerTrendsService.generateTrends(null, null, null, null))
                .thenThrow(internalFailure);

        ServletException thrown = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(get(ENDPOINT))
        );

        assertThat(thrown.getCause()).isSameAs(internalFailure);
    }

    private void stubValidationFailure(LocalDate from, LocalDate to, Integer limit) {
        when(consumerTrendsService.generateTrends(from, to, limit, null))
                .thenThrow(new ConsumerTrendsValidationException("Invalid trend criteria."));
    }

    private static ConsumerTrendsResponse representativeResponse() {
        return new ConsumerTrendsResponse(
                new TrendPeriod(FROM, TO, "Asia/Singapore"),
                new ConsumerTrendsAppliedFilters("Snacks"),
                new TrendSummary(
                        3,
                        1,
                        1,
                        1,
                        2,
                        new BigDecimal("0.43"),
                        new PeakScanDay(FROM, 3)
                ),
                List.of(new DailyTrendPoint(FROM, 3, 1, 1, 1)),
                List.of(new ProductScanTrend(
                        1,
                        "Snack A",
                        2,
                        new BigDecimal("66.67")
                )),
                List.of(new CategoryScanTrend(
                        "Snacks",
                        2,
                        new BigDecimal("66.67")
                )),
                List.of(new RestrictionTrend("PEANUT", 2)),
                List.of(new FlaggedIngredientTrend("MSG", 2)),
                new ConsumerTrendsDataQuality(false, 0),
                OffsetDateTime.parse("2026-08-08T09:15:30+08:00")
        );
    }
}
