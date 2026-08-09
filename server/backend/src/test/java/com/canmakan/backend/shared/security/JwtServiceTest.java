package com.canmakan.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
        "test-only-signing-key-32-bytes!!".getBytes(StandardCharsets.UTF_8)
    );

    private JwtProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = properties("canmakan-test", Duration.ofMinutes(15));
        jwtService = service(properties, NOW);
    }

    @Test
    void issuesMinimalIdentityTokenWithRequiredStandardClaims() {
        String token = jwtService.issueAccessToken(12L);

        Jwt jwt = jwtService.decodeAccessToken(token);

        assertEquals("12", jwt.getSubject());
        assertEquals(12L, jwtService.extractUserId(token));
        assertEquals("canmakan-test", jwt.getClaimAsString("iss"));
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), jwt.getExpiresAt());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims().keySet())
            .containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti")
            .doesNotContain(
                "email",
                "role",
                "authorities",
                "PRIMARY_ADMIN",
                "familyId",
                "profileId",
                "password",
                "passwordHash"
            );
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueAccessToken(12L);

        assertThrows(JwtException.class, () -> jwtService.decodeAccessToken(tamper(token)));
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwtService.issueAccessToken(12L);
        JwtService laterService = service(properties, NOW.plus(Duration.ofMinutes(16)));

        assertThrows(JwtException.class, () -> laterService.decodeAccessToken(token));
    }

    @Test
    void rejectsTokenFromWrongIssuer() {
        JwtService wrongIssuerService = service(
            properties("different-issuer", Duration.ofMinutes(15)),
            NOW
        );
        String token = wrongIssuerService.issueAccessToken(12L);

        assertThrows(JwtException.class, () -> jwtService.decodeAccessToken(token));
    }

    @Test
    void rejectsMissingAndInvalidSubjects() {
        String missingSubject = customToken(null);
        String invalidSubject = customToken("not-a-user-id");

        assertThrows(BadJwtException.class, () -> jwtService.decodeAccessToken(missingSubject));
        assertThrows(BadJwtException.class, () -> jwtService.decodeAccessToken(invalidSubject));
    }

    @Test
    void rejectsInvalidUserIdWhenIssuing() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.issueAccessToken(null));
        assertThrows(IllegalArgumentException.class, () -> jwtService.issueAccessToken(0L));
    }

    private String customToken(String subject) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(NOW)
            .expiresAt(NOW.plus(Duration.ofMinutes(15)))
            .id(UUID.randomUUID().toString());
        if (subject != null) {
            claims.subject(subject);
        }

        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static JwtProperties properties(String issuer, Duration ttl) {
        return new JwtProperties(issuer, ttl, TEST_SECRET);
    }

    private static JwtService service(JwtProperties properties, Instant instant) {
        return new JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String tamper(String token) {
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }
}
