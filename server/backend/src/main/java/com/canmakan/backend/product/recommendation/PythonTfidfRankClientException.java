package com.canmakan.backend.product.recommendation;

/**
 * Raised when the Python TF-IDF rank service is unavailable or returns an error.
 */
class PythonTfidfRankClientException extends RuntimeException {

    PythonTfidfRankClientException(String message) {
        super(message);
    }

    PythonTfidfRankClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
