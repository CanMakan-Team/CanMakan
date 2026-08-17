package com.canmakan.backend.product.assessment.dto;

import com.canmakan.backend.product.assessment.ExecutionTier;
import com.canmakan.backend.product.verdict.Finding;

import java.util.List;

/**
 * Result returned to the app after assessing a scanned product.
 *
 * @author XieHuayuan
 */
public record AssessmentResponse(
        String verdict,          // SAFE / WARNING / UNSAFE
        String explanation,
        List<Finding> findings,
        ExecutionTier tier,
        Long scanId,
        String productName,
        String barcode
) {
}
