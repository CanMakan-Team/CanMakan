package com.canmakan.backend.product.recommendation.dto;

public record RecommendationRequest(
	    Long profileId,
	    String sourceBarcode,
	    Long scanId
	) {}