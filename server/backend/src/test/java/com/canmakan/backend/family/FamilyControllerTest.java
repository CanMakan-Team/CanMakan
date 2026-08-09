package com.canmakan.backend.family;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyExceptionHandler;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** UC8: FamilyController HTTP contract tests */
@DisplayName("UC8: FamilyController HTTP contract tests")
class FamilyControllerTest {

    private MockMvc mockMvc;
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
        DietaryProfileService dietaryProfileService = mock(DietaryProfileService.class);
        FamilyController controller = new FamilyController(dietaryProfileService, familyService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new FamilyExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setValidator(validator)
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/families returns 201")
    void createReturns201() throws Exception {
        authenticateAs(14L);
        when(familyService.createFamily(eq(14L), any(CreateFamilyRequest.class)))
            .thenReturn(new FamilyMeResponse(50L, "Wong Family", "PRIMARY_ADMIN", 77L, 14L));

        mockMvc.perform(post("/api/families")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyName\":\"Wong Family\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.familyId").value(50))
            .andExpect(jsonPath("$.familyName").value("Wong Family"))
            .andExpect(jsonPath("$.memberRole").value("PRIMARY_ADMIN"))
            .andExpect(jsonPath("$.selfProfileId").value(77));
    }

    @Test
    @DisplayName("POST /api/families blank name returns 400 via @Valid")
    void createBlankName() throws Exception {
        authenticateAs(14L);
        mockMvc.perform(post("/api/families")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyName\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Family name is required."));

        verify(familyService, never()).createFamily(any(Long.class), any(CreateFamilyRequest.class));
    }

    @Test
    @DisplayName("POST /api/families second create returns 409")
    void createConflict() throws Exception {
        authenticateAs(4L);
        when(familyService.createFamily(eq(4L), any(CreateFamilyRequest.class)))
            .thenThrow(new AlreadyInFamilyException("You already belong to a family circle."));

        mockMvc.perform(post("/api/families")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyName\":\"Second\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("You already belong to a family circle."));
    }

    @Test
    @DisplayName("POST /api/families unknown user returns 401")
    void createUnknownUser() throws Exception {
        authenticateAs(999L);
        when(familyService.createFamily(eq(999L), any(CreateFamilyRequest.class)))
            .thenThrow(new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));

        mockMvc.perform(post("/api/families")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyName\":\"Orphan\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authenticated user was not found."));
    }

    @Test
    @DisplayName("GET /api/families/me returns 200")
    void getMeOk() throws Exception {
        authenticateAs(4L);
        when(familyService.getMyFamily(4L))
            .thenReturn(new FamilyMeResponse(1L, "Tan Family", "PRIMARY_ADMIN", 1L, 4L));

        mockMvc.perform(get("/api/families/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.familyId").value(1))
            .andExpect(jsonPath("$.familyName").value("Tan Family"));
    }

    @Test
    @DisplayName("GET /api/families/me without membership returns 404")
    void getMeNotFound() throws Exception {
        authenticateAs(99L);
        when(familyService.getMyFamily(99L))
            .thenThrow(new FamilyNotFoundException("You are not a member of a family circle."));

        mockMvc.perform(get("/api/families/me"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("You are not a member of a family circle."));
    }

    private static void authenticateAs(long userId) {
        AuthUserDetails principal = new AuthUserDetails(
            new AuthenticatedPrincipal(userId, "user" + userId + "@example.com", true, SystemRole.USER),
            "{noop}unused"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
