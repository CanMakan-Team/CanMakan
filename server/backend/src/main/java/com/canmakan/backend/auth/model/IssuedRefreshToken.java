package com.canmakan.backend.auth.model;

/** Short-lived internal carrier for a newly generated raw refresh credential. */
public record IssuedRefreshToken(String rawToken) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>]";
    }
}
