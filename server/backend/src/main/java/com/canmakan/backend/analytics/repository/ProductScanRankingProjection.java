package com.canmakan.backend.analytics.repository;

public interface ProductScanRankingProjection {

    String getBarcode();

    String getProductName();

    long getScanCount();
}
