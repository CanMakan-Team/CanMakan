package com.canmakan.backend.product.scan;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Persists thumbs up/down scan-verdict feedback (UC20), and powers the System
 * Admin "Handle User Feedback" screen's filtered listing.
 *
 * @author Kwok Heng
 */
public interface ScanFeedbackRepository extends JpaRepository<ScanFeedback, Long> {

    /**
     * Feedback rows created on or after {@code since}, joined to the reporting
     * user's email and the scanned product's name, most recent first. Every
     * other parameter is optional: pass null to skip that filter.
     *
     * @param since           lower bound (inclusive) on {@code created_at}
     * @param keyword         case-insensitive substring match against product name or user email
     * @param restrictionCode matches feedback whose scan's profile has this dietary restriction
     * @param isPositive      true for thumbs up only, false for thumbs down only, null for both
     * @param resolved        true for resolved only, false for unresolved only, null for both
     */
    @Query(value = """
        select sf.id as id,
               sf.scan_id as scanId,
               u.email as userEmail,
               coalesce(nullif(trim(p.product_name), ''), 'Unknown product') as productName,
               sf.is_positive as isPositive,
               sf.user_comments as userComments,
               sf.resolved as resolved,
               sf.created_at as createdAt
        from scans_feedback sf
        join scans s on s.id = sf.scan_id
        left join users u on u.id = s.user_id
        left join products p on p.barcode = s.barcode
        where sf.created_at >= :since
          and (:keyword is null
               or lower(coalesce(p.product_name, '')) like concat('%', lower(:keyword), '%')
               or lower(coalesce(u.email, '')) like concat('%', lower(:keyword), '%'))
          and (:restrictionCode is null or exists (
                select 1 from profile_restrictions pr
                join dietary_restrictions dr on dr.id = pr.dietary_restriction_id
                where pr.dietary_profile_id = s.profile_id and dr.code = :restrictionCode
              ))
          and (:isPositive is null or sf.is_positive = :isPositive)
          and (:resolved is null or sf.resolved = :resolved)
        order by sf.created_at desc
        """, nativeQuery = true)
    List<AdminScanFeedbackView> findForAdmin(
        @Param("since") LocalDateTime since,
        @Param("keyword") String keyword,
        @Param("restrictionCode") String restrictionCode,
        @Param("isPositive") Boolean isPositive,
        @Param("resolved") Boolean resolved
    );
}
