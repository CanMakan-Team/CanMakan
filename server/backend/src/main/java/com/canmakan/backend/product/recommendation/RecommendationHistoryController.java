package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.shared.security.AuthUserChecker;
import com.canmakan.backend.shared.security.AuthUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC17: list past product recommendations shown for an authorized dietary profile.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profiles")
public class RecommendationHistoryController {

    private final FamilyAuthorizationService familyAuthorization;
    private final RecommendationHistoryService recommendationHistoryService;

    @GetMapping("/{profileId}/recommendation-history")
    public ResponseEntity<RecommendationHistoryResponse> getRecommendationHistory(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long profileId) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        familyAuthorization.assertProfileAuthorizedForScan(userId, profileId);

        RecommendationHistoryResponse response = recommendationHistoryService.getHistoryForProfile(profileId);
        log.info(
                "GET /profiles/{}/recommendation-history → 200 count={}",
                profileId,
                response.history().size());
        return ResponseEntity.ok(response);
    }
}
