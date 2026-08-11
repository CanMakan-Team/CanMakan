package com.canmakan.backend.admin.dto;

import com.canmakan.backend.shared.security.SystemRole;
import java.time.LocalDateTime;

/** Account information exposed to System Admin account-list clients. */
public record AdminUserSummaryResponse(
        Long userId,
        String email,
        SystemRole role,
        boolean active,
        LocalDateTime updatedAt
) {
}
