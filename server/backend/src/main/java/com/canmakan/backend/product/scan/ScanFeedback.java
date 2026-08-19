package com.canmakan.backend.product.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A user-reported thumbs up/down on one scan verdict. Maps to the
 * {@code scans_feedback} table (UC20). A thumbs up never carries elaboration
 * text; {@code userComments} is optional either way.
 *
 * @author Kwok Heng
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scans_feedback")
public class ScanFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false)
    private Long scanId;

    // Named without the "is" prefix so Lombok emits the conventional
    // isPositive()/setPositive(boolean) pair instead of isIsPositive()-style names.
    @Column(name = "is_positive", nullable = false)
    private boolean positive;                // true = thumbs up, false = thumbs down

    @Column(name = "user_comments", columnDefinition = "TEXT")
    private String userComments;             // optional: the user's own words on what looks wrong

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
