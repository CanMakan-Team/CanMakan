package com.canmakan.backend.auth.dto;

import com.canmakan.backend.auth.model.IssuedRefreshToken;

/** Internal HTTP handoff containing the public body and transient cookie credential. */
public final class AuthenticationResult {

    private final AuthResponse response;
    private final IssuedRefreshToken issuedRefreshToken;

    public AuthenticationResult(AuthResponse response, IssuedRefreshToken issuedRefreshToken) {
        this.response = response;
        this.issuedRefreshToken = issuedRefreshToken;
    }

    public AuthResponse response() {
        return response;
    }

    public String rawRefreshToken() {
        return issuedRefreshToken.rawToken();
    }

    @Override
    public String toString() {
        return "AuthenticationResult[response=" + response + ", rawRefreshToken=<redacted>]";
    }
}
