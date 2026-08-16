package com.canmakan.backend.product.scan;

import java.time.LocalDateTime;

/**
 * One joined row of scan feedback plus the reporting user's email and the
 * scanned product's name, for the System Admin "Handle User Feedback" screen.
 *
 * @author Kwok Heng
 */
public interface AdminScanFeedbackView {

    Long getId();

    Long getScanId();

    String getUserEmail();

    String getProductName();

    Boolean getIsPositive();

    String getUserComments();

    Boolean getResolved();

    LocalDateTime getCreatedAt();
}
