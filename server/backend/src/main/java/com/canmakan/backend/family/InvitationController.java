package com.canmakan.backend.family;

import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserChecker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitee-facing invitation inbox APIs (UC10).
 *
 * @author Amelia
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final FamilyService familyService;

    // UC10.1 List my pending invitations
    @GetMapping("/me")
    public List<PendingInvitationResponse> listMyInvitations(
            @AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        List<PendingInvitationResponse> invitations = familyService.listMyPendingInvitations(userId);
        log.info("GET /invitations/me → 200 count={}", invitations.size());
        return invitations;
    }

    // UC10.2 Accept invitation
    @PostMapping("/{token}/accept")
    public FamilyMeResponse acceptInvitation(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable("token") String token) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        FamilyMeResponse joined = familyService.acceptInvitation(userId, token);
        log.info("POST /invitations/{}/accept → 200 familyId={}", token, joined.familyId());
        return joined;
    }

    // UC10.3 Decline invitation
    @PostMapping("/{token}/decline")
    public ResponseEntity<Void> declineInvitation(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable("token") String token) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        familyService.declineInvitation(userId, token);
        log.info("POST /invitations/{}/decline → 204", token);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
