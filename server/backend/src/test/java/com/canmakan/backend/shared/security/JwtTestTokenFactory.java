package com.canmakan.backend.shared.security;

import java.time.Clock;

/** Test-only access-token scenarios produced through the shared {@link JwtService}. */
public final class JwtTestTokenFactory {

    private JwtTestTokenFactory() {
    }

    public static String issueExpiredAccessToken(
            JwtProperties properties,
            Long userId
    ) {
        Clock expiredIssuingClock = Clock.offset(
            Clock.systemUTC(),
            properties.accessTtl().plusSeconds(1).negated()
        );
        return new JwtService(properties, expiredIssuingClock).issueAccessToken(userId);
    }
}
