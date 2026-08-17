package com.canmakan.backend.auth.service;

import com.canmakan.backend.auth.RefreshTokenProperties;
import com.canmakan.backend.auth.exception.RefreshAuthenticationException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.auth.model.RefreshToken;
import com.canmakan.backend.auth.model.RefreshTokenRotation;
import com.canmakan.backend.auth.repository.RefreshTokenRepository;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserDetailsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Generates, hashes, persists, and exclusively rotates opaque refresh tokens. */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int ENCODED_TOKEN_LENGTH = 43;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthUserDetailsService userDetailsService;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthUserDetailsService userDetailsService,
            RefreshTokenProperties properties) {
        this(
            refreshTokenRepository,
            userDetailsService,
            properties,
            Clock.systemUTC(),
            new SecureRandom()
        );
    }

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthUserDetailsService userDetailsService,
            RefreshTokenProperties properties,
            Clock clock,
            SecureRandom secureRandom) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefreshToken createSession(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        String rawToken = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(
            userId,
            hashToken(rawToken),
            clock.instant().plus(properties.ttl())
        ));
        return new IssuedRefreshToken(rawToken);
    }

    @Transactional(
        propagation = Propagation.MANDATORY,
        noRollbackFor = RefreshAuthenticationException.class
    )
    public RefreshTokenRotation rotate(String rawToken) {
        validateRawToken(rawToken);
        RefreshToken currentToken = refreshTokenRepository
            .findByTokenHashForUpdate(hashToken(rawToken))
            .orElseThrow(RefreshAuthenticationException::new);

        Instant now = clock.instant();
        if (currentToken.getExpiryDate() == null
                || !currentToken.getExpiryDate().isAfter(now)) {
            refreshTokenRepository.delete(currentToken);
            throw new RefreshAuthenticationException();
        }

        AuthUserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserById(currentToken.getUserId());
        } catch (UsernameNotFoundException exception) {
            refreshTokenRepository.delete(currentToken);
            throw new RefreshAuthenticationException();
        }
        if (!userDetails.isEnabled()) {
            refreshTokenRepository.delete(currentToken);
            throw new RefreshAuthenticationException();
        }

        refreshTokenRepository.delete(currentToken);
        IssuedRefreshToken replacement = createSession(currentToken.getUserId());
        userDetails.eraseCredentials();
        return new RefreshTokenRotation(userDetails, replacement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return 0;
        }
        return refreshTokenRepository.deleteByTokenHash(hashToken(rawToken));
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        return refreshTokenRepository.deleteAllByUserId(userId);
    }

    public static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void validateRawToken(String rawToken) {
        if (rawToken == null
                || rawToken.length() != ENCODED_TOKEN_LENGTH
                || !rawToken.matches("[A-Za-z0-9_-]+")) {
            throw new RefreshAuthenticationException();
        }
    }
}
