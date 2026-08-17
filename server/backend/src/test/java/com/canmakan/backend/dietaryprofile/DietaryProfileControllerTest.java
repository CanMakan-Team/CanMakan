package com.canmakan.backend.dietaryprofile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.dietaryprofile.dto.DietaryRestrictionDto;
import com.canmakan.backend.dietaryprofile.exception.DietaryProfileExceptionHandler;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.family.service.FamilyAuthorizationService;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.util.List;
import java.util.Map;
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

/**
 * HTTP characterization tests for dietary restriction routes (F14).
 */
@DisplayName("DietaryProfileController HTTP contract")
class DietaryProfileControllerTest {

    private MockMvc mockMvc;
    private DietaryProfileService dietaryProfileService;
    private FamilyAuthorizationService familyAuthorization;

    @BeforeEach
    void setUp() {
        dietaryProfileService = mock(DietaryProfileService.class);
        familyAuthorization = mock(FamilyAuthorizationService.class);
        DietaryProfileController controller =
            new DietaryProfileController(dietaryProfileService, familyAuthorization);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new DietaryProfileExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/restrictions returns catalog")
    void getRestrictionsReturns200() throws Exception {
        when(dietaryProfileService.getAllDietaryRestrictions()).thenReturn(List.of(
            new DietaryRestrictionDto(1L, "PEANUT", "Peanut", "ALLERGEN", "desc")));

        mockMvc.perform(get("/api/restrictions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("PEANUT"))
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/profiles/{id}/restrictions authorized returns map")
    void getProfileRestrictionsAuthorized() throws Exception {
        authenticateAs(10L);
        doNothing().when(familyAuthorization).assertProfileAuthorizedForScan(10L, 20L);
        when(dietaryProfileService.getDietaryRestrictionsForProfile(20L))
            .thenReturn(Map.of(1L, "AVOID"));

        mockMvc.perform(get("/api/profiles/20/restrictions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.1").value("AVOID"));

        verify(familyAuthorization).assertProfileAuthorizedForScan(10L, 20L);
    }

    @Test
    @DisplayName("GET /api/profiles/{id}/restrictions forbidden when authz fails")
    void getProfileRestrictionsForbidden() throws Exception {
        authenticateAs(10L);
        doThrow(new FamilyForbiddenException("Profile does not belong to your family circle."))
            .when(familyAuthorization).assertProfileAuthorizedForScan(10L, 99L);

        mockMvc.perform(get("/api/profiles/99/restrictions"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                .value("Profile does not belong to your family circle."));

        verify(dietaryProfileService, never()).getDietaryRestrictionsForProfile(any());
    }

    @Test
    @DisplayName("PUT /api/profiles/{id}/restrictions returns 204 when allowed")
    void putProfileRestrictionsAllowed() throws Exception {
        authenticateAs(10L);
        doNothing().when(familyAuthorization).assertMayEditRestrictions(10L, 20L);
        doNothing().when(dietaryProfileService)
            .saveDietaryRestrictionSelections(eq(20L), any());

        mockMvc.perform(put("/api/profiles/20/restrictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"1\":\"AVOID\"}"))
            .andExpect(status().isNoContent());

        verify(familyAuthorization).assertMayEditRestrictions(10L, 20L);
        verify(dietaryProfileService).saveDietaryRestrictionSelections(eq(20L), any());
    }

    @Test
    @DisplayName("PUT /api/profiles/{id}/restrictions forbidden for non-editor")
    void putProfileRestrictionsForbidden() throws Exception {
        authenticateAs(10L);
        doThrow(new FamilyForbiddenException(
                "You can only edit dietary restrictions for your own profile."))
            .when(familyAuthorization).assertMayEditRestrictions(10L, 20L);

        mockMvc.perform(put("/api/profiles/20/restrictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"1\":\"AVOID\"}"))
            .andExpect(status().isForbidden());

        verify(dietaryProfileService, never())
            .saveDietaryRestrictionSelections(any(), any());
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
