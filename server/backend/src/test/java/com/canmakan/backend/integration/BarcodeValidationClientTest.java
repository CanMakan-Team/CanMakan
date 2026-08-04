package com.canmakan.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.ValidationResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests product retrieval without calling live external services.
 *
 * @author YangMaowei
 */
class BarcodeValidationClientTest {

    private MockRestServiceServer offServer;
    private MockRestServiceServer eanServer;
    private BarcodeValidationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder offBuilder = RestClient.builder().baseUrl("https://off.test");
        RestClient.Builder eanBuilder = RestClient.builder().baseUrl("https://ean.test");
        offServer = MockRestServiceServer.bindTo(offBuilder).build();
        eanServer = MockRestServiceServer.bindTo(eanBuilder).build();
        client = new BarcodeValidationClient(
                offBuilder.build(),
                eanBuilder.build(),
                "test-token",
                1,
                Duration.ZERO
        );
    }

    @Test
    void fetchProductMapsTypedFieldsAndPreservesMissingNutrition() {
        offServer.expect(request -> assertTrue(
                        request.getURI().toString().startsWith("https://off.test/123.json?fields=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        ProductLookupResult result = client.fetchProduct("123").orElseThrow();

        assertEquals("123", result.barcode());
        assertEquals("Test Oat Bar", result.productName());
        assertEquals("food", result.productType());
        assertEquals("Oats", result.ingredients().getFirst().ingredientName());
        assertEquals("Oats, Sugar", result.ingredientsText());
        assertEquals("en:halal,en:organic", result.labelTags());
        assertTrue(result.ingredientDataComplete());
        assertEquals("4.5", result.nutrition().sugarsPer100g().toPlainString());
        assertEquals("0", result.nutrition().transFatPer100g().toPlainString());
        assertNull(result.nutrition().fatPer100g());
        assertNull(result.nutrition().sodiumPer100g());
        offServer.verify();
    }

    @Test
    void absentNutrimentsRemainNull() {
        offServer.expect(request -> { })
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "product": {
                            "product_name": "Unknown Nutrition",
                            "ingredients_text": "Oats",
                            "ingredients": [{"text": "Oats"}]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ProductLookupResult result = client.fetchProduct("456").orElseThrow();

        assertNull(result.nutrition());
        assertNull(result.labelTags());
    }

    @Test
    void notFoundReturnsEmptyResult() {
        offServer.expect(request -> { }).andRespond(withResourceNotFound());

        Optional<ProductLookupResult> result = client.fetchProduct("404");

        assertTrue(result.isEmpty());
    }

    @Test
    void retriesTransientServerFailureWithinBound() {
        offServer.expect(request -> { }).andRespond(withServerError());
        offServer.expect(request -> { })
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        assertTrue(client.fetchProduct("123").isPresent());

        offServer.verify();
    }

    @Test
    void validationFallsBackToTypedEanSearchResponse() {
        offServer.expect(request -> { }).andRespond(withResourceNotFound());
        eanServer.expect(request -> assertTrue(
                        request.getURI().toString().startsWith("https://ean.test/api?")))
                .andRespond(withSuccess("""
                        [{"name":"Rice Snack","categoryName":"Grocery Food"}]
                        """, MediaType.APPLICATION_JSON));

        ValidationResponse response = client.validateProduct("789");

        assertTrue(response.validFood());
        assertEquals("grocery food", response.category());
    }

    @Test
    void unsuccessfulOffResponseIsNotTreatedAsProduct() {
        offServer.expect(request -> { })
                .andRespond(withSuccess("{\"status\":\"failure\"}", MediaType.APPLICATION_JSON));

        assertFalse(client.fetchProduct("000").isPresent());
    }

    private String successResponse() {
        return """
                {
                  "status": "success",
                  "product": {
                    "product_name": "Test Oat Bar",
                    "product_type": "food",
                    "ingredients_text": "Oats, Sugar",
                    "ingredients": [
                      {"id": "en:oats", "text": "Oats"},
                      {"id": "en:sugar", "text": "Sugar"}
                    ],
                    "labels_tags": ["en:halal", "en:organic"],
                    "nutriments": {
                      "sugars_100g": 4.5,
                      "trans_fat_100g": 0,
                      "energy-kcal_100g": 210
                    }
                  }
                }
                """;
    }
}
