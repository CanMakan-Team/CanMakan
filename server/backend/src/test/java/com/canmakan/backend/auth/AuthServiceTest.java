package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.admin.exception.ProtectedAccountOperationException;
import com.canmakan.backend.auth.dto.AuthResponse;
import com.canmakan.backend.auth.dto.AuthenticationResult;
import com.canmakan.backend.auth.dto.CurrentUserResponse;
import com.canmakan.backend.auth.dto.LoginRequest;
import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.exception.AuthenticationFailedException;
import com.canmakan.backend.auth.exception.AccountSuspendedException;
import com.canmakan.backend.auth.exception.DuplicateEmailException;
import com.canmakan.backend.auth.exception.RegistrationFailedException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.auth.model.RefreshTokenRotation;
import com.canmakan.backend.auth.service.AuthService;
import com.canmakan.backend.auth.service.RefreshTokenService;
import com.canmakan.backend.family.service.FamilyInviteNotifier;
import com.canmakan.backend.family.service.InvitationRegistrationGuard;
import com.canmakan.backend.family.exception.LastPrimaryAdminException;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.JwtService;
import com.canmakan.backend.shared.security.SystemRole;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AuthService authentication and UC18 public-registration tests.
 * 
 * @author YangMaowei
 * @author Amelia
 * 
*/
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String PASSWORD_HASH = "$2a$10$test-password-hash";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private FamilyInviteNotifier familyInviteNotifier;

    @Mock
    private InvitationRegistrationGuard invitationRegistrationGuard;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(10);
        authService = new AuthService(
            userAccountRepository,
            passwordEncoder,
            authenticationManager,
            jwtService,
            refreshTokenService,
            familyInviteNotifier,
            invitationRegistrationGuard,
            familyMemberRepository
        );
    }

    @Nested
    @DisplayName("login / refresh / logout")
    class SessionLifecycle {

        @Test
        void authenticatesNormalizedEmailWithExactPasswordAndReturnsUserIdentity() {
            AuthUserDetails userDetails = userDetails(12L, "user@example.com", SystemRole.USER, true);
            when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated(userDetails));
            when(jwtService.issueAccessToken(12L)).thenReturn("signed-access-token");
            when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
            when(refreshTokenService.createSession(12L))
                .thenReturn(new IssuedRefreshToken("raw-refresh-token"));

            AuthenticationResult result = authService.login(
                new LoginRequest("  USER@EXAMPLE.COM  ", "  Exact Password1!  ")
            );
            AuthResponse response = result.response();

            ArgumentCaptor<Authentication> authenticationCaptor =
                ArgumentCaptor.forClass(Authentication.class);
            verify(authenticationManager).authenticate(authenticationCaptor.capture());
            assertEquals("user@example.com", authenticationCaptor.getValue().getPrincipal());
            assertEquals("  Exact Password1!  ", authenticationCaptor.getValue().getCredentials());
            assertEquals("signed-access-token", response.accessToken());
            assertEquals("Bearer", response.tokenType());
            assertEquals(900L, response.expiresIn());
            assertEquals(
                new CurrentUserResponse(12L, "user@example.com", SystemRole.USER, true),
                response.user()
            );
            assertEquals("raw-refresh-token", result.rawRefreshToken());
            verify(refreshTokenService).createSession(12L);
        }

        @Test
        void returnsServerDerivedAdminIdentity() {
            AuthUserDetails adminDetails = userDetails(1L, "admin@example.com", SystemRole.ADMIN, true);
            when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated(adminDetails));
            when(jwtService.issueAccessToken(1L)).thenReturn("admin-access-token");
            when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
            when(refreshTokenService.createSession(1L))
                .thenReturn(new IssuedRefreshToken("admin-refresh-token"));

            AuthResponse response = authService.login(
                new LoginRequest("admin@example.com", "Password1!")
            ).response();

            assertEquals(SystemRole.ADMIN, response.user().role());
        }

        @Test
        void convertsCredentialFailuresToTheGenericInternalSignal() {
            LoginRequest request = new LoginRequest("user@example.com", "Password1!");

            when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("wrong password"));
            assertThrows(AuthenticationFailedException.class, () -> authService.login(request));
            verifyNoInteractions(jwtService, refreshTokenService);
        }

        @Test
        void convertsVerifiedSuspendedAccountToTheDistinctInternalSignal() {
            LoginRequest request = new LoginRequest("inactive@example.com", "Password1!");

            when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new DisabledException("inactive account"));
            assertThrows(AccountSuspendedException.class, () -> authService.login(request));
            verifyNoInteractions(jwtService, refreshTokenService);
        }

        @Test
        void propagatesAuthenticationInfrastructureFailures() {
            LoginRequest request = new LoginRequest("user@example.com", "Password1!");
            InternalAuthenticationServiceException infrastructureFailure =
                new InternalAuthenticationServiceException("provider unavailable");
            when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(infrastructureFailure);

            InternalAuthenticationServiceException thrown = assertThrows(
                InternalAuthenticationServiceException.class,
                () -> authService.login(request)
            );

            assertSame(infrastructureFailure, thrown);
        }

        @Test
        void refreshUsesCurrentRotationIdentityAndIssuesANewAccessToken() {
            AuthUserDetails currentAdmin = userDetails(12L, "user@example.com", SystemRole.ADMIN, true);
            when(refreshTokenService.rotate("old-refresh-token"))
                .thenReturn(new RefreshTokenRotation(
                    currentAdmin,
                    new IssuedRefreshToken("new-refresh-token")
                ));
            when(jwtService.issueAccessToken(12L)).thenReturn("new-access-token");
            when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

            AuthenticationResult result = authService.refresh("old-refresh-token");

            assertEquals("new-access-token", result.response().accessToken());
            assertEquals(SystemRole.ADMIN, result.response().user().role());
            assertEquals("new-refresh-token", result.rawRefreshToken());
        }

        @Test
        void logoutDelegatesOnlyThePresentedRefreshCredentialForRevocation() {
            authService.logout("presented-refresh-token");

            verify(refreshTokenService).revokeSession("presented-refresh-token");
        }
    }

    @Nested
    @DisplayName("register")
    class Registration {

        @Test
        @DisplayName("UC18 BE1: creates an active USER account with a normalized email and BCrypt hash")
        void createsActiveUserWithNormalizedEmailAndBcryptHash() {
            String rawPassword = "  KeepCase Password1!  ";
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "  Person@Example.COM  ",
                rawPassword,
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
            when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
            when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount account = invocation.getArgument(0);
                account.setId(14L);
                return account;
            });
            RegistrationResponse response = authService.register(request);

            ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
            verify(userAccountRepository).saveAndFlush(accountCaptor.capture());
            UserAccount persisted = accountCaptor.getValue();

            assertEquals("person@example.com", persisted.getEmail());
            assertEquals(2L, persisted.getRoleId());
            assertTrue(persisted.isActive());
            assertNotEquals(rawPassword, persisted.getPasswordHash());
            assertTrue(passwordEncoder.matches(rawPassword, persisted.getPasswordHash()));
            assertFalse(passwordEncoder.matches(rawPassword.trim(), persisted.getPasswordHash()));
            assertTrue(persisted.getPasswordHash().matches("^\\$2[aby]\\$10\\$.*"));
            assertFalse(persisted.toString().contains(persisted.getPasswordHash()));
            assertFalse(request.toString().contains(rawPassword));

            assertEquals(14L, response.userId());
            assertEquals("person@example.com", response.email());
            assertTrue(response.active());
            verify(userAccountRepository).findRoleIdByName(AuthService.PUBLIC_REGISTRATION_ROLE);
            verify(familyInviteNotifier).hydrateIncomingInvites(14L, "person@example.com");
            verifyNoInteractions(authenticationManager, jwtService, refreshTokenService);
        }

        @Test
        @DisplayName(
            "UC18 BE1b: registration ignores legacy profile name and does not "
                + "claim invitations or start a session")
        void registrationIgnoresLegacyNameAndDoesNotClaimInvitationOrStartSession() {
            RegistrationRequest request = new RegistrationRequest(
                "Pending Profile Name",
                "person@example.com",
                "Password1!",
                "pending-invitation-token"
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
            when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
            when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount account = invocation.getArgument(0);
                account.setId(14L);
                return account;
            });
            RegistrationResponse response = authService.register(request);

            assertEquals(14L, response.userId());
            assertEquals(3, RegistrationResponse.class.getRecordComponents().length);
            verify(familyInviteNotifier).hydrateIncomingInvites(14L, "person@example.com");
            verify(invitationRegistrationGuard).requireEmailMatchesPendingInvite(
                "pending-invitation-token",
                "person@example.com"
            );
            verifyNoInteractions(authenticationManager, jwtService, refreshTokenService);
        }

        @Test
        @DisplayName("invite registration rejects an email that does not match the invitation")
        void rejectsEmailThatDoesNotMatchPendingInvitation() {
            RegistrationRequest request = new RegistrationRequest(
                null,
                "other@example.com",
                "Password1!",
                "pending-invitation-token"
            );
            org.mockito.Mockito.doThrow(
                    new com.canmakan.backend.family.exception.InvitationEmailMismatchException(
                        InvitationRegistrationGuard.MISMATCH_MESSAGE
                    )
                )
                .when(invitationRegistrationGuard)
                .requireEmailMatchesPendingInvite("pending-invitation-token", "other@example.com");

            assertThrows(
                com.canmakan.backend.family.exception.InvitationEmailMismatchException.class,
                () -> authService.register(request)
            );
            verify(userAccountRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("UC18 BE2: reports a friendly conflict before insert when email already exists")
        void rejectsExistingEmailBeforeInsert() {
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "person@example.com",
                "Password1!",
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(true);

            assertThrows(DuplicateEmailException.class, () -> authService.register(request));

            verify(userAccountRepository, never()).findRoleIdByName(any());
            verify(userAccountRepository, never()).saveAndFlush(any());
            verify(familyInviteNotifier, never()).hydrateIncomingInvites(any(Long.class), any());
        }

        @Test
        @DisplayName("UC18 BE3: translates a concurrent email UNIQUE race into duplicate conflict")
        void translatesConcurrentDuplicateInsert() {
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "person@example.com",
                "Password1!",
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
            when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
            when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062)
                ));

            assertThrows(DuplicateEmailException.class, () -> authService.register(request));
        }

        @Test
        @DisplayName("UC18 BE4: non-duplicate integrity failures remain controlled server errors")
        void doesNotMisreportOtherIntegrityFailuresAsDuplicateEmail() {
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "person@example.com",
                "Password1!",
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
            when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
            when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("unexpected integrity failure"));

            assertThrows(RegistrationFailedException.class, () -> authService.register(request));
        }

        @Test
        @DisplayName("UC18 BE5: missing USER role is a controlled configuration failure")
        void missingUserRoleIsControlledFailure() {
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "person@example.com",
                "Password1!",
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
            when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.empty());

            assertThrows(RegistrationFailedException.class, () -> authService.register(request));
            verify(userAccountRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("UC18 BE6: unexpected persistence details are wrapped by a safe failure")
        void wrapsUnexpectedPersistenceFailure() {
            RegistrationRequest request = new RegistrationRequest(
                "Person Name",
                "person@example.com",
                "Password1!",
                null
            );
            when(userAccountRepository.existsByEmail("person@example.com"))
                .thenThrow(new DataAccessResourceFailureException("internal-host:3306 password=secret"));

            RegistrationFailedException exception = assertThrows(
                RegistrationFailedException.class,
                () -> authService.register(request)
            );

            assertEquals("Registration could not be completed.", exception.getMessage());
            assertFalse(exception.getMessage().contains("internal-host"));
            assertFalse(exception.getMessage().contains("secret"));
        }
    }

    @Nested
    @DisplayName("deleteOwnAccount")
    class DeleteOwnAccount {

        private static final long CALLER_ID = 14L;
        private static final long OTHER_MEMBER_ID = 88L;
        private static final long FAMILY_ID = 3L;

        @Test
        void deactivatesCallerAndRevokesOnlyCallerSessions() {
            UserAccount caller = account(CALLER_ID, true);
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of());
            when(userAccountRepository.findByIdForUpdate(CALLER_ID)).thenReturn(Optional.of(caller));
            when(familyMemberRepository.findMembershipByUserId(CALLER_ID))
                .thenReturn(Optional.of(membership(CALLER_ID, FamilyMember.ROLE_MEMBER)));

            authService.deleteOwnAccount(CALLER_ID);

            assertFalse(caller.isActive());
            verify(refreshTokenService).revokeAllForUser(CALLER_ID);
            verify(refreshTokenService, never()).revokeAllForUser(OTHER_MEMBER_ID);
            verify(userAccountRepository, never()).findByIdForUpdate(OTHER_MEMBER_ID);
            verify(userAccountRepository).flush();
        }

        @Test
        void isIdempotentWhenCallerIsAlreadyInactive() {
            UserAccount caller = account(CALLER_ID, false);
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of());
            when(userAccountRepository.findByIdForUpdate(CALLER_ID)).thenReturn(Optional.of(caller));

            authService.deleteOwnAccount(CALLER_ID);

            assertFalse(caller.isActive());
            verify(refreshTokenService, never()).revokeAllForUser(anyLong());
            verifyNoInteractions(familyMemberRepository);
            verify(userAccountRepository, never()).flush();
        }

        @Test
        void rejectsLastFamilyPrimaryAdminWithoutDeactivating() {
            UserAccount caller = account(CALLER_ID, true);
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of());
            when(userAccountRepository.findByIdForUpdate(CALLER_ID)).thenReturn(Optional.of(caller));
            when(familyMemberRepository.findMembershipByUserId(CALLER_ID))
                .thenReturn(Optional.of(membership(CALLER_ID, FamilyMember.ROLE_PRIMARY_ADMIN)));
            when(familyMemberRepository.countActivePrimaryAdmins(FAMILY_ID)).thenReturn(1L);

            LastPrimaryAdminException thrown = assertThrows(
                LastPrimaryAdminException.class,
                () -> authService.deleteOwnAccount(CALLER_ID)
            );
            assertEquals(AuthService.LAST_FAMILY_ADMIN_MESSAGE, thrown.getMessage());
            assertTrue(caller.isActive());
            verify(refreshTokenService, never()).revokeAllForUser(anyLong());
            verify(userAccountRepository, never()).flush();
        }

        @Test
        void allowsFamilyPrimaryAdminWhenAnotherAdminExists() {
            UserAccount caller = account(CALLER_ID, true);
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of());
            when(userAccountRepository.findByIdForUpdate(CALLER_ID)).thenReturn(Optional.of(caller));
            when(familyMemberRepository.findMembershipByUserId(CALLER_ID))
                .thenReturn(Optional.of(membership(CALLER_ID, FamilyMember.ROLE_PRIMARY_ADMIN)));
            when(familyMemberRepository.countActivePrimaryAdmins(FAMILY_ID)).thenReturn(2L);

            authService.deleteOwnAccount(CALLER_ID);

            assertFalse(caller.isActive());
            verify(refreshTokenService).revokeAllForUser(CALLER_ID);
        }

        @Test
        void rejectsLastPlatformAdmin() {
            UserAccount lastAdmin = account(CALLER_ID, true);
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of(lastAdmin));

            ProtectedAccountOperationException thrown = assertThrows(
                ProtectedAccountOperationException.class,
                () -> authService.deleteOwnAccount(CALLER_ID)
            );
            assertEquals(AuthService.LAST_PLATFORM_ADMIN_MESSAGE, thrown.getMessage());
            assertTrue(lastAdmin.isActive());
            verify(userAccountRepository, never()).findByIdForUpdate(anyLong());
            verify(refreshTokenService, never()).revokeAllForUser(anyLong());
            verifyNoInteractions(familyMemberRepository);
        }

        @Test
        void throwsWhenCallerAccountIsMissing() {
            when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(List.of());
            when(userAccountRepository.findByIdForUpdate(CALLER_ID)).thenReturn(Optional.empty());

            assertThrows(
                AuthenticatedUserNotFoundException.class,
                () -> authService.deleteOwnAccount(CALLER_ID)
            );
            verify(refreshTokenService, never()).revokeAllForUser(anyLong());
            verifyNoInteractions(familyMemberRepository);
        }

        private static UserAccount account(long id, boolean active) {
            UserAccount account = new UserAccount();
            account.setId(id);
            account.setEmail("user" + id + "@example.com");
            account.setRoleId(2L);
            account.setActive(active);
            return account;
        }

        private static FamilyMember membership(long userId, String role) {
            FamilyMember member = new FamilyMember();
            member.setId(new FamilyMember.FamilyMemberId(FAMILY_ID, userId));
            member.setMemberRole(role);
            member.setIsActive(true);
            return member;
        }
    }

    private static Authentication authenticated(AuthUserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
    }

    private static AuthUserDetails userDetails(
            Long userId,
            String email,
            SystemRole role,
            boolean active) {
        return new AuthUserDetails(
            new AuthenticatedPrincipal(userId, email, active, role),
            PASSWORD_HASH
        );
    }
}
