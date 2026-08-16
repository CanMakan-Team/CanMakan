package com.canmakan.backend.admin.dto;

/**
 * Pagination metadata for a page of scan feedback rows (UC20 admin review).
 *
 * @param page       zero-based index of the returned page
 * @param pageSize   maximum rows per page
 * @param totalItems total rows matching the filters, across every page
 * @param totalPages total number of pages for {@code totalItems} at {@code pageSize}
 */
public record AdminScanFeedbackPageInfo(
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
