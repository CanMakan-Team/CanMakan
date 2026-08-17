package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.AdminScanFeedbackListResponse;
import com.canmakan.backend.admin.dto.AdminScanFeedbackPageInfo;
import com.canmakan.backend.admin.dto.AdminScanFeedbackResponse;
import com.canmakan.backend.admin.dto.AdminScanFeedbackSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateScanFeedbackResolvedResponse;
import com.canmakan.backend.admin.exception.AdminExceptionHandler;
import com.canmakan.backend.admin.exception.AdminScanFeedbackNotFoundException;
import com.canmakan.backend.admin.service.AdminScanFeedbackService;
import com.canmakan.backend.admin.service.SystemHealthService;
import com.canmakan.backend.admin.service.UserAccountManagementService;
import com.canmakan.backend.analytics.service.ConsumerTrendsService;
import com.canmakan.backend.analytics.service.UsageStatisticsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kwok Heng
 */
@DisplayName("UC20 admin review: /api/admin/scan-feedback HTTP contract")
class AdminScanFeedbackEndpointTest {

    private static final String LIST_ENDPOINT = "/api/admin/scan-feedback";

    private MockMvc mockMvc;
    private AdminScanFeedbackService adminScanFeedbackService;

    @BeforeEach
    void setUp() {
        ConsumerTrendsService consumerTrendsService = mock(ConsumerTrendsService.class);
        UserAccountManagementService userAccountManagementService = mock(UserAccountManagementService.class);
        adminScanFeedbackService = mock(AdminScanFeedbackService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(
                        consumerTrendsService,
                        userAccountManagementService,
                        mock(UsageStatisticsService.class),
                        adminScanFeedbackService,
                        mock(SystemHealthService.class)
                ))
                .setControllerAdvice(new AdminExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("returns summary, one page of items and pageInfo for the default (no-filter) request")
    void defaultRequestDelegatesNullFilters() throws Exception {
        when(adminScanFeedbackService.listFeedback(null, null, null, null, null, null, null))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(LIST_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalFeedback").value(2))
                .andExpect(jsonPath("$.summary.negativePercentage").value(50.0))
                .andExpect(jsonPath("$.summary.feedbackPerDay").value(0.07))
                .andExpect(jsonPath("$.summary.negativeFeedbackPerDay").value(0.03))
                .andExpect(jsonPath("$.items[0].userEmail").value("sarah@example.test"))
                .andExpect(jsonPath("$.items[0].productName").value("Oat Milk"))
                .andExpect(jsonPath("$.items[0].isPositive").value(true))
                .andExpect(jsonPath("$.items[1].isPositive").value(false))
                .andExpect(jsonPath("$.items[1].userComments").value("Wrong allergen listed"))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.pageSize").value(30))
                .andExpect(jsonPath("$.pageInfo.totalItems").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1));

        verify(adminScanFeedbackService).listFeedback(null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("binds keyword, restrictionCode, periodDays, isPositive, resolved, page and pageSize query params")
    void explicitFiltersBindAndDelegate() throws Exception {
        when(adminScanFeedbackService.listFeedback(
                "biryani", "HALAL", 14, false, true, 2, 10))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(LIST_ENDPOINT)
                        .param("keyword", "biryani")
                        .param("restrictionCode", "HALAL")
                        .param("periodDays", "14")
                        .param("isPositive", "false")
                        .param("resolved", "true")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());

        verify(adminScanFeedbackService).listFeedback("biryani", "HALAL", 14, false, true, 2, 10);
    }

    @Test
    @DisplayName("PATCH .../resolved delegates the new value and returns it")
    void updateResolvedDelegates() throws Exception {
        when(adminScanFeedbackService.updateResolved(7L, true))
                .thenReturn(new UpdateScanFeedbackResolvedResponse(7L, true));

        mockMvc.perform(patch("/api/admin/scan-feedback/7/resolved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.resolved").value(true));

        verify(adminScanFeedbackService).updateResolved(7L, true);
    }

    @Test
    @DisplayName("PATCH .../resolved returns 400 when resolved is missing")
    void updateResolvedRequiresResolvedField() throws Exception {
        mockMvc.perform(patch("/api/admin/scan-feedback/7/resolved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Resolved status is required."));
    }

    @Test
    @DisplayName("PATCH .../resolved returns 404 for an unknown feedback id")
    void updateResolvedRejectsUnknownId() throws Exception {
        when(adminScanFeedbackService.updateResolved(999L, true))
                .thenThrow(new AdminScanFeedbackNotFoundException(999L));

        mockMvc.perform(patch("/api/admin/scan-feedback/999/resolved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolved\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Scan feedback not found: 999"));
    }

    private static AdminScanFeedbackListResponse sampleResponse() {
        return new AdminScanFeedbackListResponse(
                new AdminScanFeedbackSummaryResponse(2, 50.0, 0.07, 0.03),
                List.of(
                        new AdminScanFeedbackResponse(
                                1L, 41L, "sarah@example.test", "Oat Milk", true, null, false,
                                LocalDateTime.of(2026, 8, 10, 9, 30)),
                        new AdminScanFeedbackResponse(
                                2L, 19L, "david@example.test", "Butter Chicken Biryani", false,
                                "Wrong allergen listed", true,
                                LocalDateTime.of(2026, 8, 13, 15, 4))
                ),
                new AdminScanFeedbackPageInfo(0, 30, 2, 1)
        );
    }
}
