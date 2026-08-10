package com.canmakan.backend.product.recommendation;

import java.math.BigDecimal;

public record AlternativeProductDto(
	    String barcode,
	    String productName,
	    String brand,
	    String matchReason,
	    BigDecimal rankScore
	) {}