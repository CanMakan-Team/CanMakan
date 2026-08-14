package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.auth.exception.RefreshAuthenticationException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.auth.model.RefreshToken;
import com.canmakan.backend.auth.model.RefreshTokenRotation;
import com.canmakan.backend.auth.repository.RefreshTokenRepository;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserDetailsService;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T04:00:00Z");
    private static final String RAW_TOKEN = "A".repeat(43);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthUserDetailsService userDetailsService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(
            refreshTokenRepository,
            userDetailsService,
            new RefreshTokenProperties(
                Duration.ofDays(7), "canmakan_refresh", true, "None"),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new FixedSecureRandom()
        );
    }

    @Test
    void createsA256BitOpaqueSessionAndPersistsOnlyItsSha256Hash() {
        IssuedRefreshToken issued = service.createSession(12L);

        ArgumentCaptor<RefreshToken> entityCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(entityCaptor.capture());
        RefreshToken entity = entityCaptor.getValue();

        assertEquals(43, issued.rawToken().length());
        assertTrue(issued.rawToken().matches("[A-Za-z0-9_-]+"));
        assertEquals(64, entity.getTokenHash().length());
        assertEquals(RefreshTokenService.hashToken(issued.rawToken()), entity.getTokenHash());
        assertNotEquals(issued.rawToken(), entity.getTokenHash());
        assertEquals(12L, entity.getUserId());
        assertEquals(NOW.plus(Duration.ofDays(7)), entity.getExpiryDate());
        assertFalse(issued.toString().contains(issued.rawToken()));
    }

    @Test
    void rotatesAValidSessionAfterReloadingCurrentAccountState() {
        RefreshToken current = token(NOW.plusSeconds(60));
        AuthUserDetails currentAdmin = userDetails(true, SystemRole.ADMIN);
        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenReturn(Optional.of(current));
        when(userDetailsService.loadUserById(12L)).thenReturn(currentAdmin);

        RefreshTokenRotation rotation = service.rotate(RAW_TOKEN);

        verify(refreshTokenRepository).delete(current);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(userDetailsService).loadUserById(12L);
        assertEquals(SystemRole.ADMIN, rotation.userDetails().getSystemRole());
        assertNotEquals(RAW_TOKEN, rotation.issuedRefreshToken().rawToken());
        assertFalse(rotation.toString().contains(rotation.issuedRefreshToken().rawToken()));
    }

    @Test
    void expiredInactiveAndMissingAccountsRevokeThePresentedSession() {
        RefreshToken expired = token(NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenReturn(Optional.of(expired));
        assertThrows(RefreshAuthenticationException.class, () -> service.rotate(RAW_TOKEN));
        verify(refreshTokenRepository).delete(expired);

        RefreshToken inactive = token(NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenReturn(Optional.of(inactive));
        when(userDetailsService.loadUserById(12L))
            .thenReturn(userDetails(false, SystemRole.USER));
        assertThrows(RefreshAuthenticationException.class, () -> service.rotate(RAW_TOKEN));
        verify(refreshTokenRepository).delete(inactive);

        RefreshToken missing = token(NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenReturn(Optional.of(missing));
        when(userDetailsService.loadUserById(12L))
            .thenThrow(new UsernameNotFoundException("not exposed"));
        assertThrows(RefreshAuthenticationException.class, () -> service.rotate(RAW_TOKEN));
        verify(refreshTokenRepository).delete(missing);
    }

    @Test
    void malformedAndUnknownTokensFailWithoutCreatingSessions() {
        assertThrows(RefreshAuthenticationException.class, () -> service.rotate("too-short"));

        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenReturn(Optional.empty());
        assertThrows(RefreshAuthenticationException.class, () -> service.rotate(RAW_TOKEN));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokesOnlyTheSessionMatchingTheExistingSha256Hash() {
        String expectedHash = RefreshTokenService.hashToken(RAW_TOKEN);
        when(refreshTokenRepository.deleteByTokenHash(expectedHash)).thenReturn(1);

        assertEquals(1, service.revokeSession(RAW_TOKEN));

        verify(refreshTokenRepository).deleteByTokenHash(expectedHash);
        verify(userDetailsService, never()).loadUserById(any());
    }

    @Test
    void missingAndBlankLogoutCredentialsAreIdempotentWithoutDatabaseAccess() {
        assertEquals(0, service.revokeSession(null));
        assertEquals(0, service.revokeSession(""));
        assertEquals(0, service.revokeSession("   "));

        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void unknownSessionDeletionIsIdempotent() {
        String expectedHash = RefreshTokenService.hashToken(RAW_TOKEN);
        when(refreshTokenRepository.deleteByTokenHash(expectedHash)).thenReturn(0);

        assertEquals(0, service.revokeSession(RAW_TOKEN));

        verify(refreshTokenRepository).deleteByTokenHash(expectedHash);
    }

    @Test
    void unexpectedPersistenceFailureIsNotReportedAsSuccessfulRevocation() {
        String expectedHash = RefreshTokenService.hashToken(RAW_TOKEN);
        when(refreshTokenRepository.deleteByTokenHash(expectedHash))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> service.revokeSession(RAW_TOKEN)
        );
    }

    @Test
    void unexpectedRotationPersistenceFailureIsNotConvertedToAuthenticationFailure() {
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException("database unavailable");
        when(refreshTokenRepository.findByTokenHashForUpdate(
                RefreshTokenService.hashToken(RAW_TOKEN)))
            .thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
            DataAccessResourceFailureException.class,
            () -> service.rotate(RAW_TOKEN)
        );

        assertEquals(failure, thrown);
        verify(refreshTokenRepository, never()).delete(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotationLookupUsesAPessimisticWriteLock() throws Exception {
        Method rotationLookup = RefreshTokenRepository.class.getMethod(
            "findByTokenHashForUpdate",
            String.class
        );

        assertEquals(
            LockModeType.PESSIMISTIC_WRITE,
            rotationLookup.getAnnotation(Lock.class).value()
        );
    }

    private static RefreshToken token(Instant expiry) {
        return new RefreshToken(12L, RefreshTokenService.hashToken(RAW_TOKEN), expiry);
    }

    private static AuthUserDetails userDetails(boolean active, SystemRole role) {
        return new AuthUserDetails(
            new AuthenticatedPrincipal(12L, "user@example.com", active, role),
            "$2a$10$test-password-hash"
        );
    }

    private static final class FixedSecureRandom extends SecureRandom {

        private byte nextValue = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, nextValue++);
        }
    }
}
