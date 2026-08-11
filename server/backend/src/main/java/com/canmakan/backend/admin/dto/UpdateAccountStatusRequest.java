package com.canmakan.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Requested active state and administrative reason for an account. */
public record UpdateAccountStatusRequest(
        @NotNull(message = "Active status is required.")
        Boolean active,
        String reason
) {
}
