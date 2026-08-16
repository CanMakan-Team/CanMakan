package com.canmakan.backend.product.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ScanFeedbackRepository#findForAdmin} and
 * {@link ScanFeedbackRepository#countNegativeForAdmin} against the real
 * database (rather than a mock), since a native query with {@code Pageable}
 * and a separate {@code countQuery} has real failure modes — a bad LIMIT/OFFSET
 * or count query — that a mocked repository can't catch (UC20 admin review).
 *
 * @author Kwok Heng
 */
@SpringBootTest
@Transactional
class ScanFeedbackRepositoryPaginationIntegrationTest {

    // Seed data covers at most the last ~15 days; a 5-year window reliably
    // includes every seeded row without depending on exactly how many exist.
    private static final LocalDateTime SINCE = LocalDateTime.now().minusYears(5);

    @Autowired
    private ScanFeedbackRepository repository;

    @Test
    @DisplayName("total element count is stable regardless of page size, and exceeds one page")
    void totalElementsIsConsistentAcrossPageSizes() {
        Page<AdminScanFeedbackView> smallPage =
                repository.findForAdmin(SINCE, null, null, null, null, PageRequest.of(0, 5));
        Page<AdminScanFeedbackView> largePage =
                repository.findForAdmin(SINCE, null, null, null, null, PageRequest.of(0, 1000));

        assertThat(smallPage.getTotalElements()).isEqualTo(largePage.getTotalElements());
        // The seed data has more than 5 rows, so a page size of 5 must not
        // silently return everything.
        assertThat(smallPage.getTotalElements()).isGreaterThan(5);
        assertThat(smallPage.getContent()).hasSize(5);
        assertThat(smallPage.getTotalPages()).isGreaterThan(1);
    }

    @Test
    @DisplayName("consecutive pages return disjoint, non-empty rows in descending created_at order")
    void pagesDoNotOverlap() {
        Page<AdminScanFeedbackView> firstPage =
                repository.findForAdmin(SINCE, null, null, null, null, PageRequest.of(0, 5));
        Page<AdminScanFeedbackView> secondPage =
                repository.findForAdmin(SINCE, null, null, null, null, PageRequest.of(1, 5));

        assertThat(firstPage.getContent()).isNotEmpty();
        assertThat(secondPage.getContent()).isNotEmpty();

        Set<Long> firstPageIds = idsOf(firstPage);
        Set<Long> secondPageIds = idsOf(secondPage);
        assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);

        LocalDateTime lastOfFirstPage = firstPage.getContent().get(firstPage.getContent().size() - 1).getCreatedAt();
        LocalDateTime firstOfSecondPage = secondPage.getContent().get(0).getCreatedAt();
        assertThat(lastOfFirstPage).isAfterOrEqualTo(firstOfSecondPage);
    }

    @Test
    @DisplayName("isPositive filter is honored on every row of a paginated page")
    void isPositiveFilterAppliesWithPagination() {
        Page<AdminScanFeedbackView> negativeOnly =
                repository.findForAdmin(SINCE, null, null, false, null, PageRequest.of(0, 5));

        assertThat(negativeOnly.getContent()).isNotEmpty();
        assertThat(negativeOnly.getContent())
                .allSatisfy(row -> assertThat(row.getIsPositive()).isFalse());
    }

    @Test
    @DisplayName("countNegativeForAdmin matches the negative rows found across every page")
    void countNegativeForAdminMatchesFullScan() {
        long negativeCount = repository.countNegativeForAdmin(SINCE, null, null, null, null);
        Page<AdminScanFeedbackView> allNegativeRows =
                repository.findForAdmin(SINCE, null, null, false, null, PageRequest.of(0, 1000));
        long totalCount = repository.findForAdmin(SINCE, null, null, null, null, PageRequest.of(0, 1))
                .getTotalElements();

        assertThat(negativeCount).isEqualTo(allNegativeRows.getTotalElements());
        assertThat(negativeCount).isPositive();
        assertThat(negativeCount).isLessThanOrEqualTo(totalCount);
    }

    private static Set<Long> idsOf(Page<AdminScanFeedbackView> page) {
        Set<Long> ids = new HashSet<>();
        page.getContent().forEach(row -> ids.add(row.getId()));
        return ids;
    }
}
