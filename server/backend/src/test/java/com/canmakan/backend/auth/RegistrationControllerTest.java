package com.canmakan.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("UC18: POST /api/auth/register HTTP contract")
class RegistrationControllerTest {

    private MockMvc mockMvc;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RegistrationController(registrationService))
            .setControllerAdvice(new RegistrationExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("UC18 HTTP1: valid registration returns 201 with only safe account fields")
    void validRegistrationReturnsSafeCreatedResponse() throws Exception {
        when(registrationService.register(any(RegistrationRequest.class)))
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

        verify(registrationService).register(
            new RegistrationRequest("Person Name", "person@example.com", "Password1!")
        );
    }

    @Test
    @DisplayName("UC18 HTTP2: invalid email returns 400 without calling the service")
    void invalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"Password1!\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid registration request."));

        verify(registrationService, never()).register(any());
    }

    @Test
    @DisplayName("UC18 HTTP3: missing password returns 400")
    void missingPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\"}"))
            .andExpect(status().isBadRequest());

        verify(registrationService, never()).register(any());
    }

    @Test
    @DisplayName("UC18 HTTP4: password shorter than eight characters returns 400")
    void shortPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\"Short1\"}"))
            .andExpect(status().isBadRequest());

        verify(registrationService, never()).register(any());
    }

    @Test
    @DisplayName("UC18 HTTP4b: missing name returns 400")
    void missingNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
            .andExpect(status().isBadRequest());

        verify(registrationService, never()).register(any());
    }

    @Test
    @DisplayName("UC18 HTTP4c: name shorter than three characters returns 400")
    void shortNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Al\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
            .andExpect(status().isBadRequest());

        verify(registrationService, never()).register(any());
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

        verify(registrationService, never()).register(any());
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

        verify(registrationService, never()).register(any());
    }

    @Test
    @DisplayName("UC18 HTTP7: duplicate email returns the frozen 409 response")
    void duplicateEmailReturnsConflict() throws Exception {
        when(registrationService.register(any(RegistrationRequest.class)))
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
        when(registrationService.register(any(RegistrationRequest.class)))
            .thenThrow(new RuntimeException("database=password=do-not-expose"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Person Name\",\"email\":\"person@example.com\",\"password\":\"Password1!\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Registration could not be completed."))
            .andExpect(content().string(not(containsString("do-not-expose"))));
    }
}
