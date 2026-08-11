package com.canmakan.backend.integration;

/**
 * Test seam for retry backoff in {@link BarcodeValidationClient}.
 * Extracted to top-level to avoid partial class loading.
 * 
 * @author YangMaowei
 */
@FunctionalInterface
interface RetrySleeper {
    void sleep(long backoffMs) throws InterruptedException;
}
