package com.canmakan.backend.product.scan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Persists and queries {@code scans}. Used to save a verdict after assessment
 * and to power the scan-history screens.
 *
 * @author XieHuayuan
 * @author Amelia
 */
public interface ScanRepository extends JpaRepository<Scan, Long> {

    /** Scan history for a user, most recent first. */
    List<Scan> findByUserIdOrderByScannedAtDesc(Long userId);

    /** Scan history for a specific dietary profile, most recent first. */
    List<Scan> findByProfileIdOrderByScannedAtDesc(Long profileId);

    /**
     * Scan history for a profile with its matching product loaded in the same
     * query, (avoids an N+1 lookup per row).
     */
    @Query("select s from Scan s left join fetch s.product where s.profileId = :profileId order by s.scannedAt desc")
    List<Scan> findByProfileIdWithProductOrderByScannedAtDesc(@Param("profileId") Long profileId);

    /**
     * Family-scoped history: scans for any of the given profiles, product joined.
     */
    @Query("""
        select s from Scan s left join fetch s.product
        where s.profileId in :profileIds
        order by s.scannedAt desc
        """)
    List<Scan> findByProfileIdInWithProductOrderByScannedAtDesc(
        @Param("profileIds") Collection<Long> profileIds);
}
