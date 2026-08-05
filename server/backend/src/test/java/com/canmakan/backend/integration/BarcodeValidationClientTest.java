package com.canmakan.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
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
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
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
    void wiresExistingProductApiPropertiesIntoSpringConstructor() throws Exception {
        Constructor<?> configuredConstructor = Arrays.stream(
                BarcodeValidationClient.class.getDeclaredConstructors()
            )
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .findFirst()
            .orElseThrow();

        List<String> propertyExpressions = Arrays.stream(configuredConstructor.getParameters())
            .map(parameter -> parameter.getAnnotation(Value.class))
            .filter(java.util.Objects::nonNull)
            .map(Value::value)
            .toList();

        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.open-food-facts-base-url}"
        ));
        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.ean-search-base-url}"
        ));
        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.connect-timeout-ms}"
        ));
        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.response-timeout-ms}"
        ));
        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.retry.max-attempts}"
        ));
        assertTrue(propertyExpressions.contains(
            "${canmakan.product-api.retry.backoff-ms}"
        ));
        assertEquals(
            List.of(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                long.class,
                long.class,
                int.class,
                long.class
            ),
            Arrays.stream(configuredConstructor.getParameterTypes()).toList()
        );
    }

    @Test
    void appliesConfiguredConnectAndReadTimeouts() {
        SimpleClientHttpRequestFactory requestFactory =
            BarcodeValidationClient.createRequestFactory(123, 456);

        assertEquals(123, ReflectionTestUtils.getField(requestFactory, "connectTimeout"));
        assertEquals(456, ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }

    @Test
    void retriesOpenFoodFactsValidationBeforeUsingFallback() {
        ClientHarness harness = clientHarness(2, 0, ignored -> { });
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withException(new IOException("connection reset")));
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
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
    void retriesEanValidationAfterOpenFoodFactsMiss() {
        ClientHarness harness = clientHarness(2, 0, ignored -> { });
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withSuccess("{\"status\":\"failure\"}", MediaType.APPLICATION_JSON));
        harness.eanServer().expect(once(), requestTo(eanUrl(BARCODE)))
            .andRespond(withException(new IOException("connection reset")));
        harness.eanServer().expect(once(), requestTo(eanUrl(BARCODE)))
            .andRespond(withSuccess(
                "[{\"name\":\"Fruit drink\",\"categoryName\":\"Food\"}]",
                MediaType.APPLICATION_JSON
            ));

        ValidationResponse response = harness.client().validateProduct(BARCODE);

        assertTrue(response.validFood());
        assertEquals("food", response.category());
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
    void classifiesNonRetryableProviderFailureWithoutRetrying() {
        ClientHarness harness = clientHarness(
            3,
            250,
            ignored -> {
                throw new AssertionError("Non-retryable failure must not back off.");
            }
        );
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
    void boundsTimeoutRetriesAndAppliesConfiguredBackoff() {
        List<Long> delays = new ArrayList<>();
        ClientHarness harness = clientHarness(3, 250, delays::add);
        harness.offServer().expect(times(3), requestTo(offUrl(BARCODE)))
            .andRespond(withException(new SocketTimeoutException("timed out")));

        ProductLookupException exception = assertThrows(
            ProductLookupException.class,
            () -> harness.client().fetchProduct(BARCODE)
        );

        assertEquals(ProductLookupException.Reason.TIMEOUT, exception.reason());
        assertEquals(List.of(250L, 250L), delays);
        harness.verify();
    }

    @Test
    void retriesTransientServerFailureForFullProductFetch() {
        ClientHarness harness = clientHarness(2, 0, ignored -> { });
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        harness.offServer().expect(once(), requestTo(offUrl(BARCODE)))
            .andRespond(withSuccess(fullProductJson(), MediaType.APPLICATION_JSON));

        ProductLookupResult result = harness.client().fetchProduct(BARCODE);

        assertEquals(BARCODE, result.barcode());
        assertEquals("Test Product", result.productName());
        harness.verify();
    }

    private static ClientHarness clientHarness() {
        return clientHarness(1, 0, ignored -> { });
    }

    private static ClientHarness clientHarness(
        int retryMaxAttempts,
        long retryBackoffMs,
        BarcodeValidationClient.RetrySleeper retrySleeper
    ) {
        RestClient.Builder offBuilder = BarcodeValidationClient.configuredRestClientBuilder(
            "https://off.test/api/v3/product/",
            100,
            200
        );
        RestClient.Builder eanBuilder = BarcodeValidationClient.configuredRestClientBuilder(
            "https://ean.test",
            100,
            200
        );
        MockRestServiceServer offServer = MockRestServiceServer.bindTo(offBuilder).build();
        MockRestServiceServer eanServer = MockRestServiceServer.bindTo(eanBuilder).build();
        BarcodeValidationClient client = new BarcodeValidationClient(
            offBuilder.build(),
            eanBuilder.build(),
            "test-token",
            new ObjectMapper(),
            retryMaxAttempts,
            retryBackoffMs,
            retrySleeper
        );
        return new ClientHarness(client, offServer, eanServer);
    }

    private static String offUrl(String barcode) {
        return "https://off.test/api/v3/product/" + barcode + ".json";
    }

    private static String eanUrl(String barcode) {
        return "https://ean.test/api?token=test-token&op=barcode-lookup&format=json&ean="
            + barcode;
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
