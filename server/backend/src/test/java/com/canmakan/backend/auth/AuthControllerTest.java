package com.canmakan.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.auth.dto.AuthResponse;
import com.canmakan.backend.auth.dto.AuthenticationResult;
import com.canmakan.backend.auth.dto.CurrentUserResponse;
import com.canmakan.backend.auth.dto.LoginRequest;
import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.exception.AuthExceptionHandler;
import com.canmakan.backend.auth.exception.AccountSuspendedException;
import com.canmakan.backend.auth.exception.AuthenticationFailedException;
import com.canmakan.backend.auth.exception.DuplicateEmailException;
import com.canmakan.backend.auth.exception.RefreshAuthenticationException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.auth.service.AuthService;
import com.canmakan.backend.auth.service.RefreshCookieService;
import com.canmakan.backend.auth.config.RefreshTokenProperties;
import com.canmakan.backend.family.exception.LastPrimaryAdminException;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import com.canmakan.backend.shared.security.CorsProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("AuthController")
class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private RefreshCookieService refreshCookieService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        refreshCookieService = mock(RefreshCookieService.class);
        when(refreshCookieService.createRefreshCookie(any(String.class)))
            .thenReturn(ResponseCookie.from("canmakan_refresh", "raw-refresh-token")
                .httpOnly(true)
                .path("/api/auth")
                .sameSite("Strict")
                .build());
        when(refreshCookieService.clearRefreshCookie())
            .thenReturn(ResponseCookie.from("canmakan_refresh", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/auth")
                .sameSite("Strict")
                .maxAge(0)
                .build());
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.example.test"));
        AuthSessionRequestGuard requestGuard = new AuthSessionRequestGuard(
            corsProperties,
            new RefreshTokenProperties(Duration.ofDays(7), "canmakan_refresh", false, "Lax")
        );
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(
                authService,
                refreshCookieService,
                requestGuard
            ))
            .defaultRequest(get("/").header(
                AuthSessionRequestGuard.SESSION_REQUEST_HEADER,
                "1"
            ))
            .setControllerAdvice(new AuthExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("login / refresh / logout")
    class SessionLifecycle {

        @Test
        void loginRejectsMissingSessionMutationHeader() throws Exception {
            MockMvc unprotectedClient = sessionLifecycleMockMvcWithoutDefaults();

            unprotectedClient.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                    .value("Authentication request origin could not be verified."));

            verify(authService, never()).login(any());
        }

        @Test
        void refreshRejectsUnauthorizedAndNullBrowserOrigins() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                    .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/auth/refresh")
                    .header("Origin", "null"))
                .andExpect(status().isForbidden());

            verify(authService, never()).refresh(any());
        }

        @Test
        void logoutAcceptsExactBrowserOriginAndNativeRequestWithoutOrigin() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/logout")
                    .header("Origin", "https://app.example.test"))
                .andExpect(status().isNoContent());
            mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
        }

        @Test
        void validLoginReturnsOnlyAccessTokenAndSafeCurrentUser() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenReturn(
                new AuthenticationResult(
                    new AuthResponse(
                        "signed-access-token",
                        "Bearer",
                        900L,
                        new CurrentUserResponse(12L, "user@example.com", SystemRole.USER, true)
                    ),
                    new IssuedRefreshToken("raw-refresh-token")
                )
            );

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "  USER@EXAMPLE.COM  ",
                          "password": "  Exact Password1!  "
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.userId").value(12))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.familyId").doesNotExist())
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(header().string(
                    "Set-Cookie",
                    containsString("canmakan_refresh=raw-refresh-token")
                ))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")));

            verify(authService).login(
                new LoginRequest("user@example.com", "  Exact Password1!  ")
            );
        }

        @Test
        void authenticationFailureReturnsGenericUnauthorizedResponse() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthenticationFailedException());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"missing@example.com\",\"password\":\"Wrong1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                    .value("Invalid credentials or account unavailable."))
                .andExpect(content().string(not(containsString("missing@example.com"))));
        }

        @Test
        void suspendedAccountReturnsSafeForbiddenWithoutTokensOrCookie() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountSuspendedException());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"inactive@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is suspended."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(content().string(not(containsString("Password1!"))))
                .andExpect(content().string(not(containsString("inactive@example.com"))))
                .andExpect(header().doesNotExist("Set-Cookie"));

            verify(refreshCookieService, never()).createRefreshCookie(any());
        }

        @Test
        void malformedLoginReturnsBadRequestWithoutCallingService() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid login request."));

            verify(authService, never()).login(any());
        }

        @Test
        void clientRoleAndIdentityFieldsAreRejected() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "user@example.com",
                          "password": "Password1!",
                          "role": "ADMIN",
                          "familyId": 1,
                          "profileId": 1
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid login request."));

            verify(authService, never()).login(any());
        }

        @Test
        void refreshUsesCookieOnlyAndReturnsRotatedCookieWithoutExposingItInJson() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("old-refresh-token"));
            when(authService.refresh("old-refresh-token")).thenReturn(
                new AuthenticationResult(
                    new AuthResponse(
                        "new-access-token",
                        "Bearer",
                        900L,
                        new CurrentUserResponse(12L, "user@example.com", SystemRole.ADMIN, true)
                    ),
                    new IssuedRefreshToken("new-refresh-token")
                )
            );
            when(refreshCookieService.createRefreshCookie("new-refresh-token"))
                .thenReturn(ResponseCookie.from("canmakan_refresh", "new-refresh-token")
                    .httpOnly(true)
                    .path("/api/auth")
                    .sameSite("Strict")
                    .build());

            mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(
                    "Set-Cookie",
                    containsString("canmakan_refresh=new-refresh-token")
                ))
                .andExpect(header().string("Set-Cookie", not(containsString("Max-Age=0"))));

            verify(authService).refresh("old-refresh-token");
            verify(refreshCookieService, never()).clearRefreshCookie();
        }

        @Test
        void missingRefreshCookieReturnsGenericUnauthorizedResponse() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(
                    "Set-Cookie",
                    containsString("canmakan_refresh=")
                ))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));

            verify(authService, never()).refresh(any());
            verify(refreshCookieService).clearRefreshCookie();
        }

        @Test
        void malformedRefreshCookieReturnsGenericUnauthorizedAndClearsIt() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("malformed-refresh-token"));
            when(authService.refresh("malformed-refresh-token"))
                .thenThrow(new RefreshAuthenticationException());

            mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(content().string(not(containsString("malformed-refresh-token"))))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

            verify(refreshCookieService).clearRefreshCookie();
        }

        @Test
        void unexpectedRefreshFailureRemainsServerErrorAndPreservesCookie() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("potentially-valid-refresh-token"));
            when(authService.refresh("potentially-valid-refresh-token"))
                .thenThrow(new IllegalStateException("database details must not escape"));

            mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                    .value("Authentication request could not be completed."))
                .andExpect(content().string(not(containsString("database details"))))
                .andExpect(header().doesNotExist("Set-Cookie"));

            verify(refreshCookieService, never()).clearRefreshCookie();
        }

        @Test
        void logoutRevokesThePresentedSessionAndReturnsOnlyAClearingCookie() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("presented-refresh-token"));

            mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                    "Set-Cookie",
                    containsString("canmakan_refresh=")
                ))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));

            verify(authService).logout("presented-refresh-token");
        }

        @Test
        void missingLogoutCookieIsIdempotentAndStillClearsTheClientCookie() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

            verify(authService).logout(null);
        }

        @Test
        void unexpectedLogoutFailureReturnsGenericServerErrorAndStillClearsCookie() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.of("presented-refresh-token"));
            doThrow(new IllegalStateException("database details must not escape"))
                .when(authService).logout("presented-refresh-token");

            mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                    .value("Authentication request could not be completed."))
                .andExpect(content().string(not(containsString("database details"))))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        }
    }

    @Nested
    @DisplayName("UC18: POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("UC18 HTTP1: valid registration returns 201 with only safe account fields")
        void validRegistrationReturnsSafeCreatedResponse() throws Exception {
            when(authService.register(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(14L, "person@example.com", true));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "  Person@Example.COM  ",
                          "password": "Password1!"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(14))
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roleId").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));

            verify(authService).register(
                new RegistrationRequest(null, "person@example.com", "Password1!", null)
            );
        }

        @Test
        @DisplayName("UC18 HTTP2: invalid email returns 400 without calling the service")
        void invalidEmailReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"not-an-email\",\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid registration request."));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP2b: email without dotted domain returns 400")
        void emailWithoutDottedDomainReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Person Name",
                          "email": "test1@abc",
                          "password": "Password1!"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid registration request."));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP3: missing password returns 400")
        void missingPasswordReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\"}"))
                .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP4: password shorter than eight characters returns 400")
        void shortPasswordReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\"Short1\"}"))
                .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP4b: legacy name is optional and ignored")
        void missingNameRemainsContractCompatible() throws Exception {
            when(authService.register(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(14L, "person@example.com", true));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(header().doesNotExist("Set-Cookie"));

            verify(authService).register(
                new RegistrationRequest(null, "person@example.com", "Password1!", null)
            );
        }

        @Test
        @DisplayName("UC18 HTTP4c: legacy name content is ignored")
        void oversizedLegacyNameIsIgnored() throws Exception {
            when(authService.register(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(14L, "person@example.com", true));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + "A".repeat(101)
                        + "\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated());

            verify(authService).register(
                new RegistrationRequest(null, "person@example.com", "Password1!", null)
            );
        }

        @Test
        @DisplayName("UC18 HTTP5: password exceeding 72 UTF-8 bytes returns 400")
        void oversizedBcryptPasswordReturnsBadRequest() throws Exception {
            String oversizedPassword = "é".repeat(37);

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\""
                        + oversizedPassword + "\"}"))
                .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP5b: weak password without special character returns 400")
        void weakPasswordReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Person Name",
                          "email": "person@example.com",
                          "password": "Password1"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid registration request."));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP6: client role, status and profile fields are rejected")
        void privilegeAndProfileFieldsAreRejected() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Person Name",
                          "email": "person@example.com",
                          "password": "Password1!",
                          "role": "ADMIN",
                          "roleId": 1,
                          "active": false,
                          "status": "ADMIN",
                          "admin": true,
                          "familyId": 1,
                          "profileId": 1,
                          "dietaryRestrictions": [1]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid registration request."));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP7: duplicate email returns the frozen 409 response")
        void duplicateEmailReturnsConflict() throws Exception {
            when(authService.register(any(RegistrationRequest.class)))
                .thenThrow(new DuplicateEmailException());

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                    .value("An account with this email already exists."));
        }

        @Test
        @DisplayName("UC18 HTTP8: unexpected errors return a generic 500 without internal details")
        void unexpectedFailureReturnsSafeInternalServerError() throws Exception {
            when(authService.register(any(RegistrationRequest.class)))
                .thenThrow(new RuntimeException("database=password=do-not-expose"));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Registration could not be completed."))
                .andExpect(content().string(not(containsString("do-not-expose"))));
        }
    }

    @Nested
    @DisplayName("DELETE /api/auth/account")
    class DeleteOwnAccount {

        @Test
        void rejectsMissingSessionMutationHeader() throws Exception {
            MockMvc unprotectedClient = sessionLifecycleMockMvcWithoutDefaults();
            authenticateAs(14L);

            unprotectedClient.perform(delete("/api/auth/account"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                    .value("Authentication request origin could not be verified."));

            verify(authService, never()).deleteOwnAccount(eq(14L));
        }

        @Test
        void rejectsUnauthorizedBrowserOrigin() throws Exception {
            authenticateAs(14L);

            mockMvc.perform(delete("/api/auth/account")
                    .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());

            verify(authService, never()).deleteOwnAccount(eq(14L));
        }

        @Test
        void deactivatesCallerOnlyAndClearsRefreshCookie() throws Exception {
            authenticateAs(14L);

            mockMvc.perform(delete("/api/auth/account"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

            verify(authService).deleteOwnAccount(14L);
        }

        @Test
        void lastFamilyAdminConflictKeepsRefreshCookie() throws Exception {
            authenticateAs(14L);
            doThrow(new LastPrimaryAdminException(AuthService.LAST_FAMILY_ADMIN_MESSAGE))
                .when(authService).deleteOwnAccount(14L);

            mockMvc.perform(delete("/api/auth/account"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                    .value(AuthService.LAST_FAMILY_ADMIN_MESSAGE))
                .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    private MockMvc sessionLifecycleMockMvcWithoutDefaults() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.example.test"));
        AuthSessionRequestGuard requestGuard = new AuthSessionRequestGuard(
            corsProperties,
            new RefreshTokenProperties(Duration.ofDays(7), "canmakan_refresh", false, "Lax")
        );
        return MockMvcBuilders
            .standaloneSetup(new AuthController(
                authService,
                refreshCookieService,
                requestGuard
            ))
            .setControllerAdvice(new AuthExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    private static void authenticateAs(long userId) {
        AuthUserDetails principal = new AuthUserDetails(
            new AuthenticatedPrincipal(
                userId,
                "user" + userId + "@example.com",
                true,
                SystemRole.USER
            ),
            "{noop}unused"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
