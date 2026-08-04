package com.canmakan.backend.product.scan;

import org.springframework.data.jpa.repository.JpaRepository;
import com.canmakan.backend.product.model.ScanProduct;

import java.util.List;
import java.util.Optional;

/**
 * Persists and queries {@code scans}. Used to save a verdict after assessment
 * and to power the scan-history screens.
 *
 * @author XieHuayuan
 */
public interface ScanRepository extends JpaRepository<Scan, Long> {

    /** Scan history for a user, most recent first. */
    List<Scan> findByUserIdOrderByScannedAtDesc(Long userId);

    /** Scan history for a specific dietary profile, most recent first. */
    List<Scan> findByProfileIdOrderByScannedAtDesc(Long profileId);

    Optional<ScanProduct> findByBarcode(String barcode);
}
