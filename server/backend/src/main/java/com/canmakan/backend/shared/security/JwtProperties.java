package com.canmakan.backend.shared.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated configuration for short-lived UC19 access tokens. */
@ConfigurationProperties(prefix = "app.security.jwt")
public final class JwtProperties {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    private final String issuer;
    private final Duration accessTtl;
    private final SecretKey signingKey;

    public JwtProperties(String issuer, Duration accessTtl, String signingSecret) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        if (accessTtl == null || accessTtl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("JWT access TTL must be positive");
        }

        byte[] keyBytes = decodeSigningSecret(signingSecret);
        if (keyBytes.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException("JWT signing secret must contain at least 32 bytes");
        }

        this.issuer = issuer.strip();
        this.accessTtl = accessTtl;
        this.signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String issuer() {
        return issuer;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    SecretKey signingKey() {
        return signingKey;
    }

    private static byte[] decodeSigningSecret(String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("JWT signing secret is required");
        }
        try {
            return Base64.getDecoder().decode(signingSecret.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT signing secret must be valid Base64", exception);
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTtl=" + accessTtl + "]";
    }
}
