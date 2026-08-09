package com.canmakan.backend.auth.model;

/** Short-lived internal carrier for a newly generated raw refresh credential. */
public final class IssuedRefreshToken {

    private final String rawToken;

    public IssuedRefreshToken(String rawToken) {
        this.rawToken = rawToken;
    }

    public String rawToken() {
        return rawToken;
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>]";
    }
}
