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

import com.canmakan.backend.shared.security.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthenticationService authenticationService;
    private RefreshCookieService refreshCookieService;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
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
            .standaloneSetup(new AuthController(authenticationService, refreshCookieService))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    @Test
    void validLoginReturnsOnlyAccessTokenAndSafeCurrentUser() throws Exception {
        when(authenticationService.login(any(LoginRequest.class))).thenReturn(
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

        verify(authenticationService).login(
            new LoginRequest("user@example.com", "  Exact Password1!  ")
        );
    }

    @Test
    void authenticationFailureReturnsGenericUnauthorizedResponse() throws Exception {
        when(authenticationService.login(any(LoginRequest.class)))
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

        verify(authenticationService, never()).login(any());
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

        verify(authenticationService, never()).login(any());
    }

    @Test
    void refreshUsesCookieOnlyAndReturnsRotatedCookieWithoutExposingItInJson() throws Exception {
        when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
            .thenReturn(Optional.of("old-refresh-token"));
        when(authenticationService.refresh("old-refresh-token")).thenReturn(
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

        verify(authenticationService).refresh("old-refresh-token");
    }

    @Test
    void missingRefreshCookieReturnsGenericUnauthorizedResponse() throws Exception {
        when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required."));

        verify(authenticationService, never()).refresh(any());
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

        verify(authenticationService).logout("presented-refresh-token");
    }

    @Test
    void missingLogoutCookieIsIdempotentAndStillClearsTheClientCookie() throws Exception {
        when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(authenticationService).logout(null);
    }

    @Test
    void unexpectedLogoutFailureReturnsGenericServerErrorAndStillClearsCookie() throws Exception {
        when(refreshCookieService.readRefreshToken(any(HttpServletRequest.class)))
            .thenReturn(Optional.of("presented-refresh-token"));
        doThrow(new IllegalStateException("database details must not escape"))
            .when(authenticationService).logout("presented-refresh-token");

        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message")
                .value("Authentication request could not be completed."))
            .andExpect(content().string(not(containsString("database details"))))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }
}
