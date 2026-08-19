package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.model.ScanProduct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A persisted scan record: the outcome of assessing one product for one dietary
 * profile. Maps to the {@code scans} table.
 *
 * @author XieHuayuan
 * @author Kwok Heng
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scans")
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "barcode", length = 50)
    private String barcode;                 // nullable: OCR-only / product not found

    // Read-only navigation to the products row sharing this scan's barcode. The
    // scalar `barcode` field above stays the one column Hibernate writes on
    // save (insertable/updatable = false here); this association exists so
    // history queries can join to the product in a single round trip instead
    // of a separate lookup per scan.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barcode", referencedColumnName = "barcode", insertable = false, updatable = false)
    private ScanProduct product;

    @Column(name = "verdict", nullable = false, length = 20)
    private String verdict;                 // "SAFE" / "WARNING" / "UNSAFE"

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings_json")
    private String findingsJson;            // JSON array of findings

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;
}
