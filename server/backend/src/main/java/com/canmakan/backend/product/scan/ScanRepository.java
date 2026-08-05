package com.canmakan.backend.product.scan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    /**
     * Scan history for a profile with its matching product loaded in the same
     * query, so the history screen needs exactly one round trip regardless of
     * how many scans the profile has (avoids an N+1 lookup per row). A left
     * join is used, not an inner join, because a scan's barcode can be null
     * (OCR-only scans) or point at a product row that has since been deleted
     * (products.barcode has ON DELETE SET NULL) — an inner join would silently
     * drop those scans from the result instead of returning them with no product.
     */
    @Query("select s from Scan s left join fetch s.product where s.profileId = :profileId order by s.scannedAt desc")
    List<Scan> findByProfileIdWithProductOrderByScannedAtDesc(@Param("profileId") Long profileId);
}
