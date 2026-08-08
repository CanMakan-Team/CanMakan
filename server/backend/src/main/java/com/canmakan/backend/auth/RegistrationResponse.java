package com.canmakan.backend.auth;

/** Safe public response returned after registration. */
public record RegistrationResponse(Long userId, Long profileId, String name, String email, boolean active) {
}
