package com.canmakan.backend.product.recommendation;

import java.util.List;

public record AlternativeProductResponse(
	    String sourceBarcode,
	    List<AlternativeProductDto> alternatives
	) {
	    public static AlternativeProductResponse empty() {
	        return new AlternativeProductResponse(null, List.of());
	    }
	    public static AlternativeProductResponse empty(String sourceBarcode) {
	        return new AlternativeProductResponse(sourceBarcode, List.of());
	    }
	}