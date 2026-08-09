package com.canmakan.backend.auth.model;

import com.canmakan.backend.shared.security.AuthUserDetails;

/** Internal result of one atomic refresh-session rotation. */
public final class RefreshTokenRotation {

    private final AuthUserDetails userDetails;
    private final IssuedRefreshToken issuedRefreshToken;

    public RefreshTokenRotation(
            AuthUserDetails userDetails,
            IssuedRefreshToken issuedRefreshToken) {
        this.userDetails = userDetails;
        this.issuedRefreshToken = issuedRefreshToken;
    }

    public AuthUserDetails userDetails() {
        return userDetails;
    }

    public IssuedRefreshToken issuedRefreshToken() {
        return issuedRefreshToken;
    }

    @Override
    public String toString() {
        return "RefreshTokenRotation[userDetails=" + userDetails
            + ", issuedRefreshToken=<redacted>]";
    }
}
