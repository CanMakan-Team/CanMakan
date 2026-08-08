package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;
import com.canmakan.backend.family.model.CreateFamilyRequest;
import com.canmakan.backend.family.model.FamilyMeResponse;
import com.canmakan.backend.family.model.FamilyRestrictionSumRes;
import com.canmakan.backend.user.UserAccount;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Family-scoped APIs including UC8 create circle and {@code /families/me}.
 *
 * Caller id is taken as {@code X-User-Id} for now. Authentication / Spring Security
 * will replace this under UC19 (e.g. {@code @AuthenticationPrincipal}).
 * 
 * @author Amelia
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/families")
public class FamilyController {

    private final DietaryProfileService dietaryProfileService;
    private final FamilyService familyService;

    // UC8 create circle by user id
    // Returns 201 Created and the created family id
    @PostMapping
    public ResponseEntity<FamilyMeResponse> createFamily(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody CreateFamilyRequest request) {
        FamilyMeResponse created = familyService.createFamily(userId, request);
        log.info("POST /families → 201 familyId={}", created.familyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // UC8 get circle by user id
    // Returns 200 OK and the family id
    @GetMapping("/me")
    public FamilyMeResponse getMyFamily(@RequestHeader("X-User-Id") Long userId) {
        FamilyMeResponse me = familyService.getMyFamily(userId);
        log.info("GET /families/me → 200 familyId={}", me.familyId());
        return me;
    }

    // UC8 get profiles by family id
    // Returns 200 OK and the list of profiles
    @GetMapping("/{familyId}/profiles")
    public List<DietaryProfileService.DietaryProfileSummaryDto> getProfilesByFamilyId(
        @PathVariable Long familyId) {
        List<DietaryProfileService.DietaryProfileSummaryDto> resp =
            dietaryProfileService.getProfilesByFamilyId(familyId);
        log.info("GET /families/{}/profiles → 200", familyId);
        return resp;
    }

    /**
     * UC6 View Family Allergy Summary Grid
     * Returns a matrix of all active family members and their dietary restrictions.
     * Accessible to any member of the family.
     */
    @GetMapping("/me/restriction-summary")
    public ResponseEntity<FamilyRestrictionSumRes> getRestrictionSummary(
            @RequestHeader("X-User-Id") Long userId
    ) {
        FamilyRestrictionSumRes summary = familyService.getFamilyRestrictionSummary(userId);
        return ResponseEntity.ok(summary);
    }
}