package com.canmakan.backend.shared.exception;

import com.canmakan.backend.integration.ProductLookupException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps product-lookup failures from assess/validate flows to HTTP status codes.
 * 
 * @author Amelia
 */
@RestControllerAdvice
public class ProductLookupExceptionHandler {

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
