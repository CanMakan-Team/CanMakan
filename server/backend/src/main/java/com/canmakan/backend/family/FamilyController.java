package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.shared.security.AuthUserDetails;

import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Family-scoped APIs including UC8 create circle and {@code /families/me}.
 *
 * Caller identity comes from the JWT principal ({@code @AuthenticationPrincipal}).
 *
 * @author Amelia
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final DietaryProfileService dietaryProfileService;
    private final FamilyService familyService;

    // UC8 create circle by user id
    // Returns 201 Created and the created family id
    @PostMapping
    public ResponseEntity<FamilyMeResponse> createFamily(
        @AuthenticationPrincipal AuthUserDetails userDetails,
        @Valid @RequestBody CreateFamilyRequest request) {
        long userId = requireUserId(userDetails);
        FamilyMeResponse created = familyService.createFamily(userId, request);
        log.info("POST /families → 201 familyId={}", created.familyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public FamilyMeResponse getMyFamily(@AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = requireUserId(userDetails);
        FamilyMeResponse me = familyService.getMyFamily(userId);
        log.info("GET /families/me → 200 familyId={}", me.familyId());
        return me;
    }

    @GetMapping("/{familyId}/profiles")
    public List<DietaryProfileService.DietaryProfileSummaryDto> getProfilesByFamilyId(
        @PathVariable Long familyId) {
        List<DietaryProfileService.DietaryProfileSummaryDto> resp =
            dietaryProfileService.getProfilesByFamilyId(familyId);
        log.info("GET /families/{}/profiles → 200", familyId);
        return resp;
    }

    /**
     * UC6 View Family Allergy Summary Grid.
     * Returns a matrix of all active family members and their dietary restrictions.
     */
    @GetMapping("/me/restriction-summary")
    public ResponseEntity<FamilyRestrictionSumRes> getRestrictionSummary(
            @AuthenticationPrincipal AuthUserDetails userDetails
    ) {
        long userId = requireUserId(userDetails);
        FamilyRestrictionSumRes summary = familyService.getFamilyRestrictionSummary(userId);
        return ResponseEntity.ok(summary);
    }

    private static long requireUserId(AuthUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new AuthenticatedUserNotFoundException("Authenticated user was not found.");
        }
        return userDetails.getUserId();
    }
}
