package com.canmakan.backend.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A catalog entry from the {@code products} table, keyed by barcode. Only the
 * columns needed for the scan-history endpoint are mapped here; the rest of the
 * Open Food Facts payload lives in the schema but has no Java consumer yet.
 *
 * @author XieHuayuan
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class ScanProduct {

    // Natural key from Open Food Facts, not a generated surrogate id.
    @Id
    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand")
    private String brand;
}
