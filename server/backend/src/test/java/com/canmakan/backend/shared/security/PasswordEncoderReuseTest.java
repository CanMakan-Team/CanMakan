package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderReuseTest {

    @Test
    void existingEncoderMatchesOnlyTheExactPassword() {
        PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();
        String rawPassword = "  KeepCase Password1!  ";
        String passwordHash = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, passwordHash));
        assertFalse(passwordEncoder.matches(rawPassword.trim(), passwordHash));
        assertFalse(passwordEncoder.matches(rawPassword.toLowerCase(), passwordHash));
    }
}
