package com.canmakan.backend.product.model;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link ScanProduct} catalog rows keyed by barcode.
 */
public interface ScanProductRepository extends JpaRepository<ScanProduct, String> {
}
