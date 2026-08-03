package com.canmakan.backend.product.scan;

import java.time.LocalDateTime;

/**
 * One row of scan history for the "view scan verdicts" screen.
 *
 * @author XieHuayuan
 */
public record ScanHistoryResponse(
        Long id,
        String barcode,
        String verdict,          // SAFE / WARNING / UNSAFE
        String explanation,
        LocalDateTime scannedAt
) {
}
