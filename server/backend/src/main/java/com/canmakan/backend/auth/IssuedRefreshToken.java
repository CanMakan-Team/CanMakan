package com.canmakan.backend.auth;

/** Short-lived internal carrier for a newly generated raw refresh credential. */
final class IssuedRefreshToken {

    private final String rawToken;

    IssuedRefreshToken(String rawToken) {
        this.rawToken = rawToken;
    }

    String rawToken() {
        return rawToken;
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>]";
    }
}
