package com.canmakan.backend.product.recommendation.dto;

import java.math.BigDecimal;

public record AlternativeProductDto(
	    String barcode,
	    String productName,
	    String brand,
	    String matchReason,
	    BigDecimal rankScore
	) {}