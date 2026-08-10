package com.canmakan.backend.dietaryprofile;

import com.canmakan.backend.dietaryprofile.dto.DietaryRestrictionDto;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.family.FamilyService;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserChecker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for dietary restriction catalog and per-profile selections (UC1 / UC12-S4).
 *
 * @author Amelia Wong
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api")
public class DietaryProfileController {

    private final DietaryProfileService dietaryProfileService;
    private final FamilyService familyService;

    @GetMapping("/restrictions")
    public List<DietaryRestrictionDto> getAllDietaryRestrictions() {
        List<DietaryRestrictionDto> resp = dietaryProfileService.getAllDietaryRestrictions();
        log.info("GET /restrictions → 200");
        return resp;
    }

    @GetMapping("/profiles/{profileId}/restrictions")
    public Map<Long, String> getDietaryRestrictionsForProfile(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long profileId) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        familyService.assertProfileAuthorizedForScan(userId, profileId);
        Map<Long, String> resp = dietaryProfileService.getDietaryRestrictionsForProfile(profileId);
        log.info("GET /profiles/{}/restrictions → 200", profileId);
        return resp;
    }

    @PutMapping("/profiles/{profileId}/restrictions")
    public ResponseEntity<Void> saveDietaryRestrictionSelections(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long profileId,
            @RequestBody Map<String, String> selections) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        familyService.assertMayEditRestrictions(userId, profileId);

        Map<Long, String> normalizedSelections = new LinkedHashMap<>();
        if (selections != null) {
            for (Map.Entry<String, String> entry : selections.entrySet()) {
                normalizedSelections.put(Long.valueOf(entry.getKey()), entry.getValue());
            }
        }

        dietaryProfileService.saveDietaryRestrictionSelections(profileId, normalizedSelections);
        log.info("PUT /profiles/{}/restrictions → 204", profileId);
        return ResponseEntity.noContent().build();
    }
}
