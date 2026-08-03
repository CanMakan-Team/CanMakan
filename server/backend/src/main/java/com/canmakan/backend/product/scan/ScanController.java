package com.canmakan.backend.product.scan;

import com.canmakan.backend.integration.BarcodeValidationClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ScanController handles API requests related to product barcode scanning and validation.
 */

@RestController
@RequestMapping("/api/scan")
public class ScanController {
    private final BarcodeValidationClient validationClient;
    
    public ScanController(BarcodeValidationClient validationClient) {
        this.validationClient = validationClient;
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateBarcode(@RequestBody ScanRequest request) {
        ValidationResponse response = validationClient.validateProduct(request.barcode());
        return ResponseEntity.ok(response);
    }
}