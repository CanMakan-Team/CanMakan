package com.canmakan.backend.admin.dto;

/** Authoritative resolved state after a System Admin updates it. */
public record UpdateScanFeedbackResolvedResponse(
        Long id,
        boolean resolved
) {
}
