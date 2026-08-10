package com.canmakan.backend.admin.dto;

import com.canmakan.backend.shared.security.SystemRole;
import java.time.LocalDateTime;

/** Authoritative account state after a System Admin status request. */
public record UpdateAccountStatusResponse(
        Long userId,
        String email,
        SystemRole role,
        boolean active,
        LocalDateTime updatedAt,
        boolean changed
) {
}
