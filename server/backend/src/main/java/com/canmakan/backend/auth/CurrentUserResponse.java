package com.canmakan.backend.auth;

import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.SystemRole;

/** Safe current-account identity returned by login and /me. */
public record CurrentUserResponse(Long userId, String email, SystemRole role) {

    public static CurrentUserResponse from(AuthUserDetails userDetails) {
        return new CurrentUserResponse(
            userDetails.getUserId(),
            userDetails.getUsername(),
            userDetails.getSystemRole()
        );
    }
}
