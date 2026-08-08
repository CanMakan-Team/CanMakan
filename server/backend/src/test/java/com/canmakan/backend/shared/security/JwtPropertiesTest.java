package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void acceptsAValidHs256ConfigurationWithoutExposingTheSecretInToString() {
        String signingSecret = base64("test-only-signing-key-32-bytes!!");

        JwtProperties properties = assertDoesNotThrow(
            () -> new JwtProperties("canmakan-test", Duration.ofMinutes(15), signingSecret)
        );

        org.junit.jupiter.api.Assertions.assertFalse(properties.toString().contains(signingSecret));
    }

    @Test
    void rejectsMissingMalformedAndWeakSigningMaterial() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new JwtProperties("canmakan-test", Duration.ofMinutes(15), "")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new JwtProperties("canmakan-test", Duration.ofMinutes(15), "not-base64")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new JwtProperties(
                "canmakan-test",
                Duration.ofMinutes(15),
                base64("too-short")
            )
        );
    }

    @Test
    void rejectsMissingIssuerAndNonPositiveTtl() {
        String signingSecret = base64("test-only-signing-key-32-bytes!!");

        assertThrows(
            IllegalArgumentException.class,
            () -> new JwtProperties(" ", Duration.ofMinutes(15), signingSecret)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new JwtProperties("canmakan-test", Duration.ZERO, signingSecret)
        );
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
