package com.canmakan.backend.product.recommendation;

import org.springframework.http.ResponseEntity;
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

    private final RecommendationService recommendationService;

    @GetMapping("/{profileId}/recommendations")
    public ResponseEntity<AlternativeProductResponse> getRecommendations(
            @PathVariable Long profileId,
            @RequestParam String sourceBarcode,
            @RequestParam(required = false) Long scanId) {

        AlternativeProductResponse response = recommendationService.recommend(
            new RecommendationRequest(profileId, sourceBarcode, scanId));
        return ResponseEntity.ok(response);
    }
}