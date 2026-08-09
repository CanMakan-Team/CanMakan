package com.canmakan.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Login controller tests
 * 
 * @author Amelia
 */
@DisplayName("UC19: POST /api/auth/login HTTP contract")
class LoginControllerTest {

    private MockMvc mockMvc;
    private LoginService loginService;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        loginService = mock(LoginService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RegistrationController(registrationService, loginService))
            .setControllerAdvice(new RegistrationExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("valid login returns 200 with session fields")
    void validLoginReturnsOk() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
            .thenReturn(new LoginResponse(
                14L,
                "Person Name",
                List.of("ROLE_APP_USER", "ROLE_FAMILY_ADMIN"),
                false
            ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "  Person@Example.COM  ",
                        "password": "Password1!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(14))
            .andExpect(jsonPath("$.displayName").value("Person Name"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_APP_USER"))
            .andExpect(jsonPath("$.roles[1]").value("ROLE_FAMILY_ADMIN"))
            .andExpect(jsonPath("$.prototype").value(false))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist());

        verify(loginService).login(new LoginRequest("person@example.com", "Password1!"));
    }

    @Test
    @DisplayName("invalid credentials return 401")
    void invalidCredentialsReturnUnauthorized() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"password\":\"WrongPassword1!\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("missing password returns 400")
    void missingPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\"}"))
            .andExpect(status().isBadRequest());

        verify(loginService, never()).login(any());
    }

    @Test
    @DisplayName("email without dotted domain returns 400")
    void emailWithoutDottedDomainReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test1@abc\",\"password\":\"Password1!\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid login request."));

        verify(loginService, never()).login(any());
    }

    @Test
    @DisplayName("unsupported fields return 400")
    void unsupportedFieldsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "person@example.com",
                        "password": "Password1!",
                        "portal": "FAMILY"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid login request."));

        verify(loginService, never()).login(any());
    }
}
