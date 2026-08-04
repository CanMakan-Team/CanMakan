package com.canmakan.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.ValidationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests validation compatibility and full product fetch without live HTTP calls.
 *
 * @author YangMaowei
 */
class BarcodeValidationClientTest {

    private static final String BARCODE = "8888888888888";

    @Test
    void keepsOpenFoodFactsFirstValidationBehavior() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"status\":\"success\",\"product\":{\"product_type\":\"snack\"}}",
                MediaType.APPLICATION_JSON
            ));

        ValidationResponse response = harness.client().validateProduct(BARCODE);

        assertTrue(response.validFood());
        assertEquals("snack", response.category());
        harness.verify();
    }

    @Test
    void keepsEanSearchFallbackValidationBehavior() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withSuccess("{\"status\":\"failure\"}", MediaType.APPLICATION_JSON));
        harness.eanServer().expect(once(), requestTo(
                "https://ean.test/api?token=test-token&op=barcode-lookup&format=json&ean=" + BARCODE
            ))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "[{\"name\":\"Fruit drink\",\"categoryName\":\"Food and grocery\"}]",
                MediaType.APPLICATION_JSON
            ));

        ValidationResponse response = harness.client().validateProduct(BARCODE);

        assertTrue(response.validFood());
        assertEquals("food and grocery", response.category());
        harness.verify();
    }

    @Test
    void rejectsNullBlankAndNonNumericBarcodesForFullFetch() {
        ClientHarness harness = clientHarness();

        for (String barcode : new String[] {null, "  ", "not-a-barcode"}) {
            ProductLookupException exception = assertThrows(
                ProductLookupException.class,
                () -> harness.client().fetchProduct(barcode)
            );
            assertEquals(ProductLookupException.Reason.INVALID_BARCODE, exception.reason());
        }

        harness.verify();
    }

    @Test
    void fetchesFullProductWhilePreservingNullZeroAndUnmappedIngredient() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andExpect(method(GET))
            .andRespond(withSuccess(fullProductJson(), MediaType.APPLICATION_JSON));

        ProductLookupResult result = harness.client().fetchProduct(BARCODE);

        assertEquals(BARCODE, result.barcode());
        assertEquals("Test Product", result.productName());
        assertEquals("food", result.productType());
        assertEquals("Sugar, mystery additive", result.ingredientsText());
        assertEquals("en:vegan,en:halal", result.labelTags());
        assertEquals(2, result.ingredients().size());
        assertEquals("Mystery additive", result.ingredients().get(1).ingredientName());
        assertNull(result.ingredients().get(1).parentAllergen());
        assertNull(result.ingredients().get(1).rootAllergen());
        assertFalse(result.ingredients().get(1).chemicalAlias());
        assertNull(result.nutrition().sugarsPer100g());
        assertEquals(BigDecimal.ZERO, result.nutrition().sodiumPer100g());
        assertTrue(result.ingredientDataComplete());
        harness.verify();
    }

    @Test
    void classifiesProductNotFound() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withResourceNotFound());

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.PRODUCT_NOT_FOUND, exception.reason());
        harness.verify();
    }

    @Test
    void classifiesMalformedProviderResponse() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.MALFORMED_RESPONSE, exception.reason());
        assertNull(exception.getCause());
        harness.verify();
    }

    @Test
    void classifiesMissingProductAsMalformedResponse() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.MALFORMED_RESPONSE, exception.reason());
        harness.verify();
    }

    @Test
    void classifiesNetworkFailureAsTransient() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withException(new IOException("connection reset")));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.TRANSIENT_FAILURE, exception.reason());
        harness.verify();
    }

    @Test
    void classifiesNonRetryableProviderFailure() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.PROVIDER_FAILURE, exception.reason());
        assertNull(exception.getCause());
        harness.verify();
    }

    @Test
    void classifiesTimeoutWithoutRetrying() {
        ClientHarness harness = clientHarness();
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withException(new SocketTimeoutException("timed out")));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.TIMEOUT, exception.reason());
        harness.verify();
    }

    private static ClientHarness clientHarness() {
        RestClient.Builder offBuilder = RestClient.builder().baseUrl(
            "https://off.test/api/v3/product/"
        );
        RestClient.Builder eanBuilder = RestClient.builder().baseUrl("https://ean.test");
        MockRestServiceServer offServer = MockRestServiceServer.bindTo(offBuilder).build();
        MockRestServiceServer eanServer = MockRestServiceServer.bindTo(eanBuilder).build();
        BarcodeValidationClient client = new BarcodeValidationClient(
            offBuilder.build(),
            eanBuilder.build(),
            "test-token",
            new ObjectMapper()
        );
        return new ClientHarness(client, offServer, eanServer);
    }

    private static String offUrl(String barcode) {
        return "https://off.test/api/v3/product/" + barcode + ".json";
    }

    private static String fullProductJson() {
        return """
            {
              "status": "success",
              "product": {
                "product_name": "Test Product",
                "product_type": "food",
                "ingredients_text": "Sugar, mystery additive",
                "ingredients": [
                  {"id": "en:sugar", "text": "Sugar"},
                  {"id": "en:mystery-additive", "text": "Mystery additive"}
                ],
                "labels_tags": ["en:vegan", "en:halal"],
                "nutriments": {
                  "sugars_100g": null,
                  "sodium_100g": 0
                }
              }
            }
            """;
    }

    private record ClientHarness(
        BarcodeValidationClient client,
        MockRestServiceServer offServer,
        MockRestServiceServer eanServer
    ) {
        void verify() {
            offServer.verify();
            eanServer.verify();
        }
    }
}
