package com.canmakan.backend.auth;

/** Successful UC19 login response containing only an access token and safe identity. */
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    CurrentUserResponse user
) {
}
