package com.canmakan.backend.auth;

import com.canmakan.backend.shared.security.AuthUserDetails;

/** Internal result of one atomic refresh-session rotation. */
final class RefreshTokenRotation {

    private final AuthUserDetails userDetails;
    private final IssuedRefreshToken issuedRefreshToken;

    RefreshTokenRotation(
            AuthUserDetails userDetails,
            IssuedRefreshToken issuedRefreshToken) {
        this.userDetails = userDetails;
        this.issuedRefreshToken = issuedRefreshToken;
    }

    AuthUserDetails userDetails() {
        return userDetails;
    }

    IssuedRefreshToken issuedRefreshToken() {
        return issuedRefreshToken;
    }

    @Override
    public String toString() {
        return "RefreshTokenRotation[userDetails=" + userDetails
            + ", issuedRefreshToken=<redacted>]";
    }
}
