package com.canmakan.backend.shared.exception;

import com.canmakan.backend.integration.ProductLookupException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles product lookup exceptions
 * 
 * @author Amelia
 */
@RestControllerAdvice
public class ProductLookupExceptionHandler {

    /**
     * Handles product lookup exceptions and returns a response entity with the appropriate status code and message.
     * @param exception the product lookup exception
     * @return a response entity with the appropriate status code and message
     */
    @ExceptionHandler(ProductLookupException.class)
    public ResponseEntity<Map<String, String>> handleProductLookup(ProductLookupException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_BARCODE -> HttpStatus.BAD_REQUEST;
            case PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TIMEOUT, TRANSIENT_FAILURE, INTERRUPTED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status)
            .body(Map.of("message", exception.getMessage() == null
                ? "Product lookup failed."
                : exception.getMessage()));
    }

}
