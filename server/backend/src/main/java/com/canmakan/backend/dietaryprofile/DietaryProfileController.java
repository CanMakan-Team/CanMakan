package com.canmakan.backend.dietaryprofile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API controller for frontend features concerning dietary profiles.
 * 
 * @author Amelia Wong
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api")
public class DietaryProfileController {

    private final DietaryProfileService dietaryProfileService;

    // Returns list of restrictions
    @GetMapping("/restrictions")
    public List<DietaryProfileService.DietaryRestrictionDto> getAllDietaryRestrictions() {
        log.info("GET /restrictions → 200");
        return dietaryProfileService.getAllDietaryRestrictions();
    }

    // Returns restrictions set for specific profile
    @GetMapping("/profiles/{profileId}/restrictions")
    public Map<Long, String> getDietaryRestrictionsForProfile(@PathVariable Long profileId) {
        log.info("GET /profiles/{profileId}/restrictions → 200");
        return dietaryProfileService.getDietaryRestrictionsForProfile(profileId);
    }

    // Inserts/updates saved dietary restrictions for specific profile
    @PutMapping("/profiles/{profileId}/restrictions")
    public ResponseEntity<Void> saveDietaryRestrictionSelections(
            @PathVariable Long profileId,
            @RequestBody Map<String, String> selections) {

        Map<Long, String> normalizedSelections = new LinkedHashMap<>();
        if (selections != null) {
            for (Map.Entry<String, String> entry : selections.entrySet()) {
                normalizedSelections.put(Long.valueOf(entry.getKey()), entry.getValue());
            }
        }

        dietaryProfileService.saveDietaryRestrictionSelections(profileId, normalizedSelections);
        log.info("PUT /profiles/{profileId}/restrictions → 204");
        return ResponseEntity.noContent().build();
    }

}
