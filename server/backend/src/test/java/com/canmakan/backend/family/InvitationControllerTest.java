package com.canmakan.backend.family;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.InvitationPreviewResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyExceptionHandler;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.InvitationExpiredException;
import com.canmakan.backend.family.exception.InvitationNotFoundException;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * UC10 invitation inbox HTTP contract tests.
 *
 * @author Amelia
 */
@DisplayName("UC10 InvitationController HTTP contract")
class InvitationControllerTest {

    private MockMvc mockMvc;
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
        InvitationController controller = new InvitationController(familyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new FamilyExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/invitations/me returns pending list")
    void listOk() throws Exception {
        authenticateAs(30L);
        when(familyService.listMyPendingInvitations(30L)).thenReturn(List.of(
            new PendingInvitationResponse(
                5L,
                1L,
                "Host Family",
                "admin@example.com",
                "tok",
                "ABCD1234",
                InvitationStatus.PENDING,
                Instant.parse("2026-08-17T00:00:00Z"),
                false)
        ));

        mockMvc.perform(get("/api/invitations/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].familyName").value("Host Family"))
            .andExpect(jsonPath("$[0].invitationToken").value("tok"))
            .andExpect(jsonPath("$[0].expired").value(false));
    }

    @Test
    @DisplayName("POST /api/invitations/{token}/accept returns 200")
    void acceptOk() throws Exception {
        authenticateAs(30L);
        when(familyService.acceptInvitation(30L, "tok"))
            .thenReturn(new FamilyMeResponse(1L, "Host Family", "MEMBER", 99L, 10L));

        mockMvc.perform(post("/api/invitations/tok/accept"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.familyId").value(1))
            .andExpect(jsonPath("$.memberRole").value("MEMBER"));
    }

    @Test
    @DisplayName("POST accept expired returns 410")
    void acceptExpired() throws Exception {
        authenticateAs(30L);
        when(familyService.acceptInvitation(eq(30L), eq("tok")))
            .thenThrow(new InvitationExpiredException("Invitation has expired."));

        mockMvc.perform(post("/api/invitations/tok/accept"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("Invitation has expired."));
    }

    @Test
    @DisplayName("POST accept email mismatch returns 403")
    void acceptForbidden() throws Exception {
        authenticateAs(30L);
        when(familyService.acceptInvitation(eq(30L), eq("tok")))
            .thenThrow(new FamilyForbiddenException(
                "Invitation email does not match the authenticated user."));

        mockMvc.perform(post("/api/invitations/tok/accept"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST accept already in family returns 409")
    void acceptConflict() throws Exception {
        authenticateAs(30L);
        when(familyService.acceptInvitation(eq(30L), eq("tok")))
            .thenThrow(new AlreadyInFamilyException("You already belong to a family circle."));

        mockMvc.perform(post("/api/invitations/tok/accept"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST accept unknown token returns 404")
    void acceptNotFound() throws Exception {
        authenticateAs(30L);
        when(familyService.acceptInvitation(eq(30L), eq("missing")))
            .thenThrow(new InvitationNotFoundException("Invitation was not found."));

        mockMvc.perform(post("/api/invitations/missing/accept"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/invitations/{token}/decline returns 204")
    void declineOk() throws Exception {
        authenticateAs(30L);

        mockMvc.perform(post("/api/invitations/tok/decline"))
            .andExpect(status().isNoContent());

        verify(familyService).declineInvitation(30L, "tok");
    }

    @Test
    @DisplayName("GET /api/invitations/{token}/preview returns invited email")
    void previewOk() throws Exception {
        when(familyService.previewInvitation("tok")).thenReturn(
            new InvitationPreviewResponse("jamie@example.com", "Wong Family", false)
        );

        mockMvc.perform(get("/api/invitations/tok/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitedEmail").value("jamie@example.com"))
            .andExpect(jsonPath("$.familyName").value("Wong Family"))
            .andExpect(jsonPath("$.expired").value(false));
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
