package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.AdminScanFeedbackListResponse;
import com.canmakan.backend.admin.dto.UpdateScanFeedbackResolvedResponse;
import com.canmakan.backend.admin.exception.AdminScanFeedbackNotFoundException;
import com.canmakan.backend.product.scan.AdminScanFeedbackView;
import com.canmakan.backend.product.scan.ScanFeedback;
import com.canmakan.backend.product.scan.ScanFeedbackRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Kwok Heng
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC20 admin review: AdminScanFeedbackService")
class AdminScanFeedbackServiceTest {

    @Mock
    private ScanFeedbackRepository scanFeedbackRepository;

    private AdminScanFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AdminScanFeedbackService(scanFeedbackRepository);
    }

    @Test
    @DisplayName("summarizes totals, negative percentage and per-day rates over the filtered set")
    void summarizesFilteredResults() {
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(
                        view(1L, true, null),
                        view(2L, false, "Wrong allergen"),
                        view(3L, false, null),
                        view(4L, false, "Outdated ingredient list")
                ));

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, 10, null, null);

        assertThat(response.items()).hasSize(4);
        assertThat(response.summary().totalFeedback()).isEqualTo(4);
        assertThat(response.summary().negativePercentage()).isEqualTo(75.0);
        assertThat(response.summary().feedbackPerDay()).isEqualTo(0.4);
        assertThat(response.summary().negativeFeedbackPerDay()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("defaults the period to 30 days when none is supplied")
    void defaultsPeriodTo30Days() {
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(view(1L, true, null)));

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, null, null, null);

        // 1 item / 30-day default period, rounded to 2dp by the service.
        assertThat(response.summary().feedbackPerDay()).isEqualTo(0.03);
    }

    @Test
    @DisplayName("blank keyword and restriction code are normalized to null before querying")
    void blankFiltersAreNormalizedToNull() {
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), any(), any()))
                .thenReturn(List.of());

        service.listFeedback("   ", "  ", 7, true, false);

        verify(scanFeedbackRepository).findForAdmin(any(), isNull(), isNull(), eq(true), eq(false));
    }

    @Test
    @DisplayName("returns zero-valued summary stats when nothing matches the filters")
    void handlesEmptyResultSet() {
        when(scanFeedbackRepository.findForAdmin(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        AdminScanFeedbackListResponse response = service.listFeedback("nomatch", null, 7, null, null);

        assertThat(response.summary().totalFeedback()).isZero();
        assertThat(response.summary().negativePercentage()).isZero();
        assertThat(response.summary().feedbackPerDay()).isZero();
        assertThat(response.summary().negativeFeedbackPerDay()).isZero();
    }

    @Test
    @DisplayName("updateResolved saves the new resolved flag")
    void updateResolvedSavesNewFlag() {
        ScanFeedback feedback = new ScanFeedback();
        feedback.setId(5L);
        feedback.setResolved(false);
        when(scanFeedbackRepository.findById(5L)).thenReturn(Optional.of(feedback));
        when(scanFeedbackRepository.save(feedback)).thenReturn(feedback);

        UpdateScanFeedbackResolvedResponse response = service.updateResolved(5L, true);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.resolved()).isTrue();
        assertThat(feedback.isResolved()).isTrue();
    }

    @Test
    @DisplayName("updateResolved throws AdminScanFeedbackNotFoundException for an unknown id")
    void updateResolvedRejectsUnknownId() {
        when(scanFeedbackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateResolved(99L, true))
                .isInstanceOf(AdminScanFeedbackNotFoundException.class);
    }

    private static AdminScanFeedbackView view(Long id, boolean isPositive, String comment) {
        return new AdminScanFeedbackView() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getScanId() {
                return id;
            }

            @Override
            public String getUserEmail() {
                return "user" + id + "@example.test";
            }

            @Override
            public String getProductName() {
                return "Product " + id;
            }

            @Override
            public Boolean getIsPositive() {
                return isPositive;
            }

            @Override
            public String getUserComments() {
                return comment;
            }

            @Override
            public Boolean getResolved() {
                return false;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return LocalDateTime.of(2026, 8, 10, 9, 30);
            }
        };
    }
}
