package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.shared.security.AuthUserChecker;
import com.canmakan.backend.shared.security.AuthUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class RecommendationController {

    private final FamilyAuthorizationService familyAuthorization;
    private final RecommendationService recommendationService;

    @GetMapping("/{profileId}/recommendations")
    public ResponseEntity<AlternativeProductResponse> getRecommendations(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long profileId,
            @RequestParam String sourceBarcode,
            @RequestParam(required = false) Long scanId) {

        long userId = AuthUserChecker.requireUserId(userDetails);
        familyAuthorization.assertProfileAuthorizedForScan(userId, profileId);

        AlternativeProductResponse response = recommendationService.recommend(
            new RecommendationRequest(profileId, sourceBarcode, scanId));
        return ResponseEntity.ok(response);
    }
}
