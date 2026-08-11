package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5: AlternativeProductQueryService")
class AlternativeProductQueryServiceTest {

    @Mock
    private CatalogProductRepository catalogProductRepository;

    private AlternativeProductQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AlternativeProductQueryService(catalogProductRepository);
    }

    @Test
    void findCandidatesUsesSameCategoryAndExcludesSource() {
        CatalogProduct source = product("100", "Breakfast cereals", "Oats, Wheat");
        CatalogProduct candidate = product("200", "Breakfast cereals", "Rice flour");

        when(catalogProductRepository.findCandidatesByCategory("Breakfast cereals", "100"))
                .thenReturn(List.of(candidate));

        List<CatalogProduct> results = queryService.findCandidates(source);

        assertEquals(1, results.size());
        assertEquals("200", results.getFirst().getBarcode());
        verify(catalogProductRepository).findCandidatesByCategory("Breakfast cereals", "100");
    }

    @Test
    void findCandidatesReturnsEmptyWhenSourceNotEligible() {
        CatalogProduct source = product("100", null, "Oats");

        assertTrue(queryService.findCandidates(source).isEmpty());
        assertTrue(queryService.findCandidates(null).isEmpty());
    }

    @Test
    void findCandidatesLimitsToFiftyRows() {
        CatalogProduct source = product("100", "Groceries", "Salt");
        List<CatalogProduct> many = IntStream.range(0, 60)
                .mapToObj(i -> product(String.valueOf(200 + i), "Groceries", "Salt"))
                .toList();

        when(catalogProductRepository.findCandidatesByCategory("Groceries", "100"))
                .thenReturn(many);

        assertEquals(50, queryService.findCandidates(source).size());
    }

    @Test
    void findByBarcodeDelegatesToRepository() {
        CatalogProduct product = product("100", "Groceries", "Salt");
        when(catalogProductRepository.findById("100")).thenReturn(Optional.of(product));

        assertTrue(queryService.findByBarcode("100").isPresent());
        assertEquals("100", queryService.findByBarcode("100").get().getBarcode());
    }

    private static CatalogProduct product(String barcode, String category, String ingredients) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Product " + barcode);
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        return product;
    }
}
