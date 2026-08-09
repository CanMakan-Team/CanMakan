package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;
import com.canmakan.backend.family.dto.ClaimInvitationRequest;
import com.canmakan.backend.family.dto.CreateDependantProfileRequest;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.CreateInvitationRequest;
import com.canmakan.backend.family.dto.DependantProfileResponse;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.UserSearchResponse;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.shared.security.AuthUserDetails;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Family-scoped APIs: UC8 create/me, UC9 invite/dependant, UC6 restriction summary.
 * Caller identity comes from the JWT principal.
 * 
 * @author Amelia
 * @author Khai
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final DietaryProfileService dietaryProfileService;
    private final FamilyService familyService;

    // POST /api/families -> create a new family
    @PostMapping
    public ResponseEntity<FamilyMeResponse> createFamily(
        @AuthenticationPrincipal AuthUserDetails userDetails,
        @Valid @RequestBody CreateFamilyRequest request) {
        long userId = requireUserId(userDetails);
        FamilyMeResponse created = familyService.createFamily(userId, request);
        log.info("POST /families → 201 familyId={}", created.familyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // GET /api/families/me -> get the family for the authenticated user
    @GetMapping("/me")
    public FamilyMeResponse getMyFamily(@AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = requireUserId(userDetails);
        FamilyMeResponse me = familyService.getMyFamily(userId);
        log.info("GET /families/me → 200 familyId={}", me.familyId());
        return me;
    }

    // GET /api/families/{familyId}/profiles -> get the profiles for a family
    @GetMapping("/{familyId}/profiles")
    public List<DietaryProfileService.DietaryProfileSummaryDto> getProfilesByFamilyId(
        @PathVariable Long familyId) {
        List<DietaryProfileService.DietaryProfileSummaryDto> resp =
            dietaryProfileService.getProfilesByFamilyId(familyId);
        log.info("GET /families/{}/profiles → 200", familyId);
        return resp;
    }

    // GET /api/families/me/restriction-summary -> get the restriction summary for the authenticated user
    @GetMapping("/me/restriction-summary")
    public ResponseEntity<FamilyRestrictionSumRes> getRestrictionSummary(
            @AuthenticationPrincipal AuthUserDetails userDetails
    ) {
        long userId = requireUserId(userDetails);
        FamilyRestrictionSumRes summary = familyService.getFamilyRestrictionSummary(userId);
        return ResponseEntity.ok(summary);
    }

    // GET /api/families/me/user-search -> search for a user by email
    @GetMapping("/me/user-search")
    public UserSearchResponse searchUser(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam("email") String email) {
        long userId = requireUserId(userDetails);
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Email must be valid.");
        }
        UserSearchResponse result = familyService.searchUserByEmail(userId, normalized);
        log.info("GET /families/me/user-search → 200 status={}", result.accountStatus());
        return result;
    }

    // POST /api/families/me/invitations -> create a new invitation
    @PostMapping("/me/invitations")
    public ResponseEntity<InvitationResponse> createInvitation(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Valid @RequestBody CreateInvitationRequest request) {
        long userId = requireUserId(userDetails);
        InvitationResponse created = familyService.createInvitation(userId, request);
        log.info("POST /families/me/invitations → 201 invitationId={}", created.invitationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // POST /api/families/me/invitations/claim -> claim an invitation
    @PostMapping("/me/invitations/claim")
    public FamilyMeResponse claimInvitation(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Valid @RequestBody ClaimInvitationRequest request) {
        long userId = requireUserId(userDetails);
        FamilyMeResponse claimed = familyService.claimInvitation(userId, request);
        log.info("POST /families/me/invitations/claim → 200 familyId={}", claimed.familyId());
        return claimed;
    }

    // POST /api/families/me/profiles -> create a new dependant profile
    @PostMapping("/me/profiles")
    public ResponseEntity<DependantProfileResponse> createDependantProfile(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Valid @RequestBody CreateDependantProfileRequest request) {
        long userId = requireUserId(userDetails);
        DependantProfileResponse created = familyService.createDependantProfile(userId, request);
        log.info("POST /families/me/profiles → 201 profileId={}", created.profileId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // --- Helper methods ---

    // Require the user id from the authenticated user
    private static long requireUserId(AuthUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new AuthenticatedUserNotFoundException("Authenticated user was not found.");
        }
        return userDetails.getUserId();
    }
}
