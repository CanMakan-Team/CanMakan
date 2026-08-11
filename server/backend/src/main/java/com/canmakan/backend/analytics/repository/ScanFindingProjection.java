package com.canmakan.backend.analytics.repository;

public interface ScanFindingProjection {

    Long getScanId();

    String getFindingsJson();
}
