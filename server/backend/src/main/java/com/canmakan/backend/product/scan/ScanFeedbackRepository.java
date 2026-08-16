package com.canmakan.backend.product.scan;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists thumbs up/down scan-verdict feedback (UC20), and powers the System
 * Admin "Handle User Feedback" screen's filtered, paginated listing.
 *
 * @author Kwok Heng
 */
public interface ScanFeedbackRepository extends JpaRepository<ScanFeedback, Long> {

    /**
     * Shared {@code FROM}/{@code WHERE} clause for the admin listing, count and
     * negative-count queries below. Every filter is optional (matches when the
     * bound parameter is null). Factored out as a compile-time constant — used
     * verbatim by three {@code @Query} annotations — purely so that shared SQL
     * isn't triplicated in the source; it changes nothing about how the
     * queries execute.
     */
    String ADMIN_FEEDBACK_FROM_WHERE = """
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
        """;

    /**
     * One page of feedback rows created on or after {@code since}, joined to
     * the reporting user's email and the scanned product's name, most recent
     * first. Every filter parameter is optional: pass null to skip it. Bounded
     * by {@code pageable} (LIMIT/OFFSET) so payload and query cost stay fixed
     * regardless of how much feedback has accumulated; {@link Page#getTotalElements()}
     * gives the full filtered count for building the summary cards.
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
        """ + ADMIN_FEEDBACK_FROM_WHERE + """
        order by sf.created_at desc
        """,
        countQuery = "select count(*) " + ADMIN_FEEDBACK_FROM_WHERE,
        nativeQuery = true)
    Page<AdminScanFeedbackView> findForAdmin(
        @Param("since") LocalDateTime since,
        @Param("keyword") String keyword,
        @Param("restrictionCode") String restrictionCode,
        @Param("isPositive") Boolean isPositive,
        @Param("resolved") Boolean resolved,
        Pageable pageable
    );

    /**
     * Count of negative (thumbs down) rows matching the same filters as
     * {@link #findForAdmin}, used to build the summary cards without pulling
     * every matching row into memory.
     */
    @Query(value = "select count(*) " + ADMIN_FEEDBACK_FROM_WHERE + " and sf.is_positive = false",
        nativeQuery = true)
    long countNegativeForAdmin(
        @Param("since") LocalDateTime since,
        @Param("keyword") String keyword,
        @Param("restrictionCode") String restrictionCode,
        @Param("isPositive") Boolean isPositive,
        @Param("resolved") Boolean resolved
    );
}
