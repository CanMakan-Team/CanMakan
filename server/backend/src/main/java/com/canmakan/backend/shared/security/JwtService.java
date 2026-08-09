package com.canmakan.backend.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

/** Issues and validates identity-only UC19 access JWTs. */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withSecretKey(properties.signingKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            timestampValidator,
            new JwtIssuerValidator(properties.issuer())
        ));
        this.decoder = jwtDecoder;
    }

    public String issueAccessToken(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }

        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTtl()))
            .id(UUID.randomUUID().toString())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
            .type("JWT")
            .build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt decodeAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Invalid access token");
        }
        Jwt jwt = decoder.decode(token);
        extractUserId(jwt);
        return jwt;
    }

    public Long extractUserId(String token) {
        return extractUserId(decodeAccessToken(token));
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    private static Long extractUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || !subject.matches("[1-9][0-9]*")) {
            throw new BadJwtException("Invalid access token subject");
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BadJwtException("Invalid access token subject", exception);
        }
    }
}
