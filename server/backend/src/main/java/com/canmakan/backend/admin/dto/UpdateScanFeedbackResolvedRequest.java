package com.canmakan.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Requested resolved state for one scan feedback row. */
public record UpdateScanFeedbackResolvedRequest(
        @NotNull(message = "Resolved status is required.")
        Boolean resolved
) {
}
