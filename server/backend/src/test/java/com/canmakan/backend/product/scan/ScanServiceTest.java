package com.canmakan.backend.product.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.model.ScanProductRepository;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("UC3: ScanService - Saves the outcome of a scan into the scans table after the DietaryRuleEngine produces a SafetyVerdict")
@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ScanRepository scanRepository;
    @Mock
    private ScanProductRepository scanProductRepository;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(scanRepository, scanProductRepository, new ObjectMapper());
        when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> {
            Scan scan = invocation.getArgument(0);
            scan.setId(42L);
            return scan;
        });
    }

    // Test case: insertsCatalogStubWhenBarcodeMissingFromProducts
    @Test
    void insertsCatalogStubWhenBarcodeMissingFromProducts() {
        when(scanProductRepository.existsById("3017620422003")).thenReturn(false);

        scanService.saveScan(
                1L,
                1L,
                "3017620422003",
                SafetyVerdict.safe("ok", List.of()),
                "Nutella"
        );

        ArgumentCaptor<ScanProduct> productCaptor = ArgumentCaptor.forClass(ScanProduct.class);
        verify(scanProductRepository).save(productCaptor.capture());
        assertEquals("3017620422003", productCaptor.getValue().getBarcode());
        assertEquals("Nutella", productCaptor.getValue().getProductName());
    }

    // Test case: skipsCatalogInsertWhenProductAlreadyExists
    @Test
    void skipsCatalogInsertWhenProductAlreadyExists() {
        when(scanProductRepository.existsById("3017620422003")).thenReturn(true);

        scanService.saveScan(
                1L,
                1L,
                "3017620422003",
                SafetyVerdict.safe("ok", List.of()),
                "Nutella"
        );

        verify(scanProductRepository, never()).save(any());
    }
}
