package com.canmakan.backend.user.repository;

import java.time.LocalDateTime;

/** User and system-role fields required by the System Admin account list. */
public interface AdminUserSummaryView {

    Long getUserId();

    String getEmail();

    String getRole();

    Boolean getActive();

    LocalDateTime getUpdatedAt();
}
