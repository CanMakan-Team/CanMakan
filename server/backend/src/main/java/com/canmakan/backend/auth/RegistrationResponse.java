package com.canmakan.backend.auth;

/** Safe public response returned after registration. */
public record RegistrationResponse(Long userId, String email, boolean active) {
}
