package com.canmakan.backend.family;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto;
import com.canmakan.backend.family.dto.ActiveProfileResponse;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyExceptionHandler;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;

import java.util.List;
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

/** UC8: FamilyController HTTP contract tests 
 * 
 * @author Amelia
*/
@DisplayName("UC8 - 11 test cases: FamilyController HTTP contract")
class FamilyControllerTest {

    private MockMvc mockMvc;
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
        FamilyController controller = new FamilyController(familyService);
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

    @Test
    @DisplayName("GET /api/families/me/user-search returns 200")
    void userSearchOk() throws Exception {
        authenticateAs(10L);
        when(familyService.searchUserByEmail(10L, "new@example.com"))
            .thenReturn(new com.canmakan.backend.family.dto.UserSearchResponse(
                null, null, "n***w@example.com", "NOT_REGISTERED", "NOT_LINKED"));

        mockMvc.perform(get("/api/families/me/user-search").param("email", "new@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountStatus").value("NOT_REGISTERED"));
    }

    @Test
    @DisplayName("POST /api/families/me/invitations returns 201")
    void createInvitationOk() throws Exception {
        authenticateAs(10L);
        when(familyService.createInvitation(
                eq(10L), any(com.canmakan.backend.family.dto.CreateInvitationRequest.class)))
            .thenReturn(new com.canmakan.backend.family.dto.InvitationResponse(
                1L,
                "new@example.com",
                "token",
                "ABCD1234",
                "http://localhost:5173/invite/token",
                com.canmakan.backend.family.model.InvitationStatus.PENDING,
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                false));

        mockMvc.perform(post("/api/families/me/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.inviteCode").value("ABCD1234"))
            .andExpect(jsonPath("$.inviteUrl").value("http://localhost:5173/invite/token"));
    }

    @Test
    @DisplayName("POST /api/families/me/profiles returns 201")
    void createDependantOk() throws Exception {
        authenticateAs(10L);
        when(familyService.createDependantProfile(
                eq(10L), any(com.canmakan.backend.family.dto.CreateDependantProfileRequest.class)))
            .thenReturn(new com.canmakan.backend.family.dto.DependantProfileResponse(
                55L, "Child", "CHILD", 1L));

        mockMvc.perform(post("/api/families/me/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"Child\",\"relationship\":\"CHILD\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.profileId").value(55));
    }

    @Test
    @DisplayName("GET /api/families/me/members returns 200")
    void listMembersOk() throws Exception {
        authenticateAs(10L);
        when(familyService.listFamilyMembers(10L)).thenReturn(List.of(
            new com.canmakan.backend.family.dto.FamilyMemberRosterDto(
                10L,
                77L,
                10L,
                "Admin",
                "SELF",
                "UNSPECIFIED",
                List.of("HALAL"),
                List.of("PEANUT_ALLERGY"),
                "REGISTERED_USER",
                "a***n@example.com",
                "PRIMARY_ADMIN",
                true),
            new com.canmakan.backend.family.dto.FamilyMemberRosterDto(
                2L,
                2L,
                null,
                "Toddler",
                "CHILD",
                "UNSPECIFIED",
                List.of(),
                List.of(),
                "DEPENDANT_PROFILE",
                null,
                null,
                true)
        ));

        mockMvc.perform(get("/api/families/me/members"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].memberId").value(10))
            .andExpect(jsonPath("$[0].source").value("REGISTERED_USER"))
            .andExpect(jsonPath("$[1].memberId").value(2))
            .andExpect(jsonPath("$[1].source").value("DEPENDANT_PROFILE"));
    }

    @Test
    @DisplayName("GET /api/families/me/members without membership returns 404")
    void listMembersNotFound() throws Exception {
        authenticateAs(99L);
        when(familyService.listFamilyMembers(99L))
            .thenThrow(new FamilyNotFoundException("You are not a member of a family circle."));

        mockMvc.perform(get("/api/families/me/members"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("You are not a member of a family circle."));
    }

    @Test
    @DisplayName("GET /api/families/{familyId}/profiles returns 200 for member")
    void getProfilesByFamilyIdOk() throws Exception {
        authenticateAs(10L);
        when(familyService.getProfilesForFamilyMember(10L, 1L)).thenReturn(List.of(
            new DietaryProfileSummaryDto(77L, "Admin", 1L, "SELF", "AD", true, true)
        ));

        mockMvc.perform(get("/api/families/1/profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(77))
            .andExpect(jsonPath("$[0].profileName").value("Admin"));
    }

    @Test
    @DisplayName("GET /api/families/{familyId}/profiles returns 403 for another family")
    void getProfilesByFamilyIdForbidden() throws Exception {
        authenticateAs(10L);
        when(familyService.getProfilesForFamilyMember(10L, 99L))
            .thenThrow(new FamilyForbiddenException(
                "Profile list is not available for this family."));

        mockMvc.perform(get("/api/families/99/profiles"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                .value("Profile list is not available for this family."));
    }

    @Test
    @DisplayName("GET /api/families/me/active-profile returns 200")
    void getActiveProfileOk() throws Exception {
        authenticateAs(10L);
        when(familyService.getActiveProfile(10L))
            .thenReturn(new ActiveProfileResponse(77L, "Admin", "SELF", 1L, true));

        mockMvc.perform(get("/api/families/me/active-profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileId").value(77))
            .andExpect(jsonPath("$.profileName").value("Admin"));
    }

    @Test
    @DisplayName("PUT /api/families/me/active-profile returns 200")
    void setActiveProfileOk() throws Exception {
        authenticateAs(10L);
        when(familyService.setActiveProfile(eq(10L), eq(88L)))
            .thenReturn(new ActiveProfileResponse(88L, "Child", "CHILD", 1L, false));

        mockMvc.perform(put("/api/families/me/active-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":88}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileId").value(88));
    }

    @Test
    @DisplayName("GET /api/families/me/profiles returns 200")
    void listMyProfilesOk() throws Exception {
        authenticateAs(10L);
        when(familyService.listMyFamilyProfiles(10L)).thenReturn(List.of(
            new DietaryProfileSummaryDto(77L, "Admin", 1L, "SELF", "AD", true, true),
            new DietaryProfileSummaryDto(88L, "Child", 1L, "CHILD", "CH", false, false)
        ));

        mockMvc.perform(get("/api/families/me/profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(77))
            .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    @DisplayName("PUT /api/families/me/profiles/{id} returns 200")
    void updateProfileOk() throws Exception {
        authenticateAs(10L);
        when(familyService.updateProfileMetadata(
                eq(10L), eq(88L), any(com.canmakan.backend.family.dto.UpdateProfileRequest.class)))
            .thenReturn(new com.canmakan.backend.family.dto.FamilyMemberRosterDto(
                88L, 88L, null, "Toddler", "CHILD", "UNSPECIFIED",
                List.of(), List.of(), "DEPENDANT_PROFILE", null, null, true));

        mockMvc.perform(put("/api/families/me/profiles/88")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"Toddler\",\"relationship\":\"CHILD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileName").value("Toddler"))
            .andExpect(jsonPath("$.profileId").value(88));
    }

    @Test
    @DisplayName("PATCH /api/families/me/profiles/{id} returns 200")
    void setProfileActiveOk() throws Exception {
        authenticateAs(10L);
        when(familyService.setProfileActive(10L, 88L, false))
            .thenReturn(new DietaryProfileSummaryDto(88L, "Child", 1L, "CHILD", "CH", false, false));

        mockMvc.perform(patch("/api/families/me/profiles/88")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/families/me/members/{userId} returns 204")
    void removeMemberOk() throws Exception {
        authenticateAs(10L);

        mockMvc.perform(delete("/api/families/me/members/20"))
            .andExpect(status().isNoContent());

        verify(familyService).removeFamilyMember(10L, 20L);
    }

    @Test
    @DisplayName("DELETE /api/families/me/profiles/{id} returns 204")
    void removeDependantOk() throws Exception {
        authenticateAs(10L);

        mockMvc.perform(delete("/api/families/me/profiles/88"))
            .andExpect(status().isNoContent());

        verify(familyService).removeDependantProfile(10L, 88L);
    }

    @Test
    @DisplayName("GET /api/families/me/scans returns 200")
    void listScansOk() throws Exception {
        authenticateAs(10L);
        when(familyService.listFamilyScans(10L)).thenReturn(List.of(
            new com.canmakan.backend.family.dto.FamilyScanHistoryDto(
                501L,
                "Crunchy Peanut Bar",
                "Good Day",
                10L,
                "Admin",
                "AVOID",
                "",
                "",
                "",
                "Peanut matched",
                "COMPLETE",
                "Open Food Facts / assessment",
                "2026-07-28T18:42:00",
                null)
        ));

        mockMvc.perform(get("/api/families/me/scans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].scanId").value(501))
            .andExpect(jsonPath("$[0].verdict").value("AVOID"));
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
