package com.canmakan.backend.product.verdict;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
