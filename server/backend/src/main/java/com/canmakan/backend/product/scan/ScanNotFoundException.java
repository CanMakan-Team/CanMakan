package com.canmakan.backend.product.scan;

/**
 * Thrown when a referenced scan id does not exist (HTTP 404).
 *
 * @author Kwok Heng
 */
public class ScanNotFoundException extends RuntimeException {

    public ScanNotFoundException(String message) {
        super(message);
    }
}
