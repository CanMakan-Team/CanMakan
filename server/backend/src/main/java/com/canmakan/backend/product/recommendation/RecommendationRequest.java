package com.canmakan.backend.product.recommendation;

public record RecommendationRequest(
	    Long profileId,
	    String sourceBarcode,
	    Long scanId
	) {}