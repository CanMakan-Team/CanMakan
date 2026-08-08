package com.canmakan.backend.auth;

/** Internal HTTP handoff containing the public body and transient cookie credential. */
final class AuthenticationResult {

    private final AuthResponse response;
    private final IssuedRefreshToken issuedRefreshToken;

    AuthenticationResult(AuthResponse response, IssuedRefreshToken issuedRefreshToken) {
        this.response = response;
        this.issuedRefreshToken = issuedRefreshToken;
    }

    AuthResponse response() {
        return response;
    }

    String rawRefreshToken() {
        return issuedRefreshToken.rawToken();
    }

    @Override
    public String toString() {
        return "AuthenticationResult[response=" + response + ", rawRefreshToken=<redacted>]";
    }
}
