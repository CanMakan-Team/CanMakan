package com.canmakan.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.canmakan.backend.auth.exception.AuthenticationFailedException;
import com.canmakan.backend.auth.exception.DuplicateEmailException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.shared.security.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
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
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService, refreshCookieService))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    @Nested
    @DisplayName("login / refresh / logout")
    class SessionLifecycle {

        @Test
        void validLoginReturnsOnlyAccessTokenAndSafeCurrentUser() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenReturn(
                new AuthenticationResult(
                    new AuthResponse(
                        "signed-access-token",
                        "Bearer",
                        900L,
                        new CurrentUserResponse(12L, "user@example.com", SystemRole.USER)
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
                        new CurrentUserResponse(12L, "user@example.com", SystemRole.ADMIN)
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
                ));

            verify(authService).refresh("old-refresh-token");
        }

        @Test
        void missingRefreshCookieReturnsGenericUnauthorizedResponse() throws Exception {
            when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

            verify(authService, never()).refresh(any());
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
                .thenReturn(new RegistrationResponse(14L, 77L, "Person Name", "person@example.com", true));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Person Name",
                          "email": "  Person@Example.COM  ",
                          "password": "Password1!"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(14))
                .andExpect(jsonPath("$.profileId").value(77))
                .andExpect(jsonPath("$.name").value("Person Name"))
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roleId").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

            verify(authService).register(
                new RegistrationRequest("Person Name", "person@example.com", "Password1!", null)
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
        @DisplayName("UC18 HTTP4b: missing name returns 400")
        void missingNameReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("UC18 HTTP4c: name shorter than three characters returns 400")
        void shortNameReturnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Al\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
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
}

