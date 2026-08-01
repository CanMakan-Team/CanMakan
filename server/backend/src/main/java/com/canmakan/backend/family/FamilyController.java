package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API controller for family-scoped operations.
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api")
public class FamilyController {

    private final DietaryProfileService dietaryProfileService;

    @GetMapping("/families/{familyId}/profiles")
    public List<DietaryProfileService.DietaryProfileSummaryDto> getProfilesByFamilyId(@PathVariable Long familyId) {
        List<DietaryProfileService.DietaryProfileSummaryDto> resp = dietaryProfileService.getProfilesByFamilyId(familyId);
        log.info("GET /families/{familyId}/profiles → 200");
        return resp;
    }
}
