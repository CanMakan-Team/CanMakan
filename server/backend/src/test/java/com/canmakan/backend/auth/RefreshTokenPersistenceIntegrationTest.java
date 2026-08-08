package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class RefreshTokenPersistenceIntegrationTest {

    private static final Long USER_ID = 4L;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        refreshTokenService.revokeAllForUser(USER_ID);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenService.revokeAllForUser(USER_ID);
    }

    @Test
    void persistsHashExpiryAndDatabaseCreatedTimestamp() {
        Instant beforeCreation = Instant.now();
        IssuedRefreshToken issued = createSession();
        Instant afterCreation = Instant.now();
        RefreshToken stored = refreshTokenRepository.findAllByUserId(USER_ID).getFirst();

        assertNotEquals(issued.rawToken(), stored.getTokenHash());
        assertEquals(RefreshTokenService.hashToken(issued.rawToken()), stored.getTokenHash());
        assertTrue(stored.getExpiryDate().isAfter(beforeCreation.plus(Duration.ofDays(6))));
        assertTrue(stored.getExpiryDate().isBefore(afterCreation.plus(Duration.ofDays(8))));
        assertNotNull(stored.getCreatedAt());
    }

    @Test
    void permitsIndependentSessionsForTheSameUser() {
        IssuedRefreshToken first = createSession();
        IssuedRefreshToken second = createSession();

        assertNotEquals(first.rawToken(), second.rawToken());
        assertEquals(2L, refreshTokenRepository.countByUserId(USER_ID));
    }

    @Test
    void simultaneousReuseProducesExactlyOneSuccessfulRotation() throws Exception {
        String originalRawToken = createSession().rawToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            List<Future<AuthenticationResult>> futures = new ArrayList<>();
            for (int request = 0; request < 2; request++) {
                futures.add(executor.submit(() -> {
                    bothReady.countDown();
                    if (!startTogether.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent refresh start timed out");
                    }
                    return authenticationService.refresh(originalRawToken);
                }));
            }
            assertTrue(bothReady.await(10, TimeUnit.SECONDS));
            startTogether.countDown();

            int successes = 0;
            int rejectedReuses = 0;
            AuthenticationResult successfulRotation = null;
            for (Future<AuthenticationResult> future : futures) {
                try {
                    successfulRotation = future.get(20, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof RefreshAuthenticationException) {
                        rejectedReuses++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertEquals(1, successes);
            assertEquals(1, rejectedReuses);
            assertNotNull(successfulRotation);
            assertFalse(refreshTokenRepository.existsByTokenHash(
                RefreshTokenService.hashToken(originalRawToken)
            ));
            assertTrue(refreshTokenRepository.existsByTokenHash(
                RefreshTokenService.hashToken(successfulRotation.rawRefreshToken())
            ));
            assertEquals(1L, refreshTokenRepository.countByUserId(USER_ID));
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousLogoutOfTheSameSessionIsIdempotent() throws Exception {
        String rawToken = createSession().rawToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int request = 0; request < 2; request++) {
                futures.add(executor.submit(() -> {
                    bothReady.countDown();
                    if (!startTogether.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent logout start timed out");
                    }
                    authenticationService.logout(rawToken);
                    return null;
                }));
            }
            assertTrue(bothReady.await(10, TimeUnit.SECONDS));
            startTogether.countDown();

            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertFalse(refreshTokenRepository.existsByTokenHash(
                RefreshTokenService.hashToken(rawToken)
            ));
            assertEquals(0L, refreshTokenRepository.countByUserId(USER_ID));
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private IssuedRefreshToken createSession() {
        return transactionTemplate.execute(status -> refreshTokenService.createSession(USER_ID));
    }
}
