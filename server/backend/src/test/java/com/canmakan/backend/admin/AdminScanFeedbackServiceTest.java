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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    @DisplayName("summarizes totals, negative percentage and per-day rates over the full filtered set, "
            + "not just the returned page")
    void summarizesFilteredResults() {
        // Page size 2 of a 4-row filtered set (3 negative) — the summary must
        // reflect the full 4/3 count, not just this page's 2 rows.
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(view(1L, true, null), view(2L, false, "Wrong allergen")),
                        PageRequest.of(0, 2),
                        4));
        when(scanFeedbackRepository.countNegativeForAdmin(any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(3L);

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, 10, null, null, 0, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.summary().totalFeedback()).isEqualTo(4);
        assertThat(response.summary().negativePercentage()).isEqualTo(75.0);
        assertThat(response.summary().feedbackPerDay()).isEqualTo(0.4);
        assertThat(response.summary().negativeFeedbackPerDay()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("defaults the period to 30 days when none is supplied")
    void defaultsPeriodTo30Days() {
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(view(1L, true, null)), PageRequest.of(0, 30), 1));
        when(scanFeedbackRepository.countNegativeForAdmin(any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, null, null, null, null, null);

        // 1 item / 30-day default period, rounded to 2dp by the service.
        assertThat(response.summary().feedbackPerDay()).isEqualTo(0.03);
    }

    @Test
    @DisplayName("blank keyword and restriction code are normalized to null before querying")
    void blankFiltersAreNormalizedToNull() {
        when(scanFeedbackRepository.findForAdmin(any(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(scanFeedbackRepository.countNegativeForAdmin(any(), isNull(), isNull(), any(), any()))
                .thenReturn(0L);

        service.listFeedback("   ", "  ", 7, true, false, null, null);

        verify(scanFeedbackRepository).findForAdmin(any(), isNull(), isNull(), eq(true), eq(false), any());
    }

    @Test
    @DisplayName("returns zero-valued summary stats when nothing matches the filters")
    void handlesEmptyResultSet() {
        when(scanFeedbackRepository.findForAdmin(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(scanFeedbackRepository.countNegativeForAdmin(any(), any(), any(), any(), any()))
                .thenReturn(0L);

        AdminScanFeedbackListResponse response = service.listFeedback("nomatch", null, 7, null, null, null, null);

        assertThat(response.summary().totalFeedback()).isZero();
        assertThat(response.summary().negativePercentage()).isZero();
        assertThat(response.summary().feedbackPerDay()).isZero();
        assertThat(response.summary().negativeFeedbackPerDay()).isZero();
    }

    @Test
    @DisplayName("defaults to page 0 with 30 rows per page when neither is supplied")
    void defaultsPagination() {
        when(scanFeedbackRepository.findForAdmin(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view(1L, true, null)), PageRequest.of(0, 30), 1));
        when(scanFeedbackRepository.countNegativeForAdmin(any(), any(), any(), any(), any()))
                .thenReturn(0L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, null, null, null, null, null);

        verify(scanFeedbackRepository).findForAdmin(any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
        assertThat(response.pageInfo().page()).isZero();
        assertThat(response.pageInfo().pageSize()).isEqualTo(30);
        assertThat(response.pageInfo().totalItems()).isEqualTo(1);
        assertThat(response.pageInfo().totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("uses the explicit page and pageSize when supplied")
    void usesExplicitPagination() {
        when(scanFeedbackRepository.findForAdmin(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 25));
        when(scanFeedbackRepository.countNegativeForAdmin(any(), any(), any(), any(), any()))
                .thenReturn(0L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        AdminScanFeedbackListResponse response = service.listFeedback(null, null, null, null, null, 2, 10);

        verify(scanFeedbackRepository).findForAdmin(any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(response.pageInfo().page()).isEqualTo(2);
        assertThat(response.pageInfo().pageSize()).isEqualTo(10);
        assertThat(response.pageInfo().totalItems()).isEqualTo(25);
        assertThat(response.pageInfo().totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("clamps a negative page and a non-positive pageSize back to the defaults")
    void clampsInvalidPagination() {
        when(scanFeedbackRepository.findForAdmin(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(scanFeedbackRepository.countNegativeForAdmin(any(), any(), any(), any(), any()))
                .thenReturn(0L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        service.listFeedback(null, null, null, null, null, -1, 0);

        verify(scanFeedbackRepository).findForAdmin(any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
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
