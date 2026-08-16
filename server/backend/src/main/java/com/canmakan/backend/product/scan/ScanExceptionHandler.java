package com.canmakan.backend.product.scan;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error translation specific to {@link ScanController}. Authorization
 * failures shared with other domains (family/profile access) are handled by
 * {@link com.canmakan.backend.shared.exception.GlobalExceptionHandler}.
 *
 * @author Kwok Heng
 */
@RestControllerAdvice(assignableTypes = ScanController.class)
public class ScanExceptionHandler {

    @ExceptionHandler(ScanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleScanNotFound(ScanNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }
}
