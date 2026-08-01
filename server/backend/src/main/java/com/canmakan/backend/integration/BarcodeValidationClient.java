package com.canmakan.backend.integration;

import com.canmakan.backend.product.scan.ValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for interacting with the barcode validation service.
 */
@Service
public class BarcodeValidationClient {
    private final RestClient offRestClient;
    private final RestClient eanRestClient;
    private final String eanSearchToken;

    public BarcodeValidationClient(
        @Value("${app.api.ean-search.token}") String eanSearchToken,
        @Value("${app.name:CanMakan}") String appName,
        @Value("${app.version:1.0}") String appVersion,
        @Value("${app.contact.email:khairulanwar.kamaruzaman@u.nus.edu}") String contactEmail
    ) {
        // Construct the custom User-Agent string: AppName/Version (ContactEmail)
        String userAgent = String.format("%s/v%s - (%s)", appName, appVersion, contactEmail);

        // Primary: Open Food Facts (Configured with Custom User-Agent)
        this.offRestClient = RestClient.builder()
                .baseUrl("https://world.openfoodfacts.org/api/v3/product/")
                .defaultHeader("User-Agent", userAgent)
                .build();

        // Fallback: EAN-Search (Configured with base domain root)
        this.eanRestClient = RestClient.builder()
                .baseUrl("https://api.ean-search.org")
                .build();
                
        this.eanSearchToken = eanSearchToken;
    }

    public ValidationResponse validateProduct(String barcode) {
        // 1. Primary Lookup: Open Food Facts
        try {
            JsonNode offResponse = offRestClient.get()
                .uri(barcode + ".json")
                .retrieve()
                .body(JsonNode.class);

            if (offResponse != null && offResponse.has("status")) {
                String status = offResponse.get("status").asText();
                if ("success".equalsIgnoreCase(status) || "1".equals(status)) {
                    JsonNode product = offResponse.get("product");
                    String category = (product != null && product.has("product_type")) 
                            ? product.get("product_type").asText() 
                            : "food";
                    return new ValidationResponse(true, category, "Valid food product found in Open Food Facts.");
                } 
            }
        } catch (RestClientException e) {
            // Product not found in OFF or API error; suppress and proceed to fallback
        }

        // 2. Fallback Lookup: EAN-Search
        try {
            // Explicitly define the "/api" path in the uriBuilder to avoid concatenation bugs
            JsonNode eanResponse = eanRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api")
                    .queryParam("token", eanSearchToken)
                    .queryParam("op", "barcode-lookup")
                    .queryParam("format", "json")
                    .queryParam("ean", barcode)
                    .build())
                .retrieve()
                .body(JsonNode.class);

            if (eanResponse != null && eanResponse.isArray() && !eanResponse.isEmpty()) {
                JsonNode item = eanResponse.get(0);
                
                if (item.has("error")) {
                     return new ValidationResponse(false, "Unknown", "Product not found in fallback database.");
                }

                // Use path() instead of get() to handle missing elements gracefully without NullPointerExceptions
                String name = item.path("name").asText("").toLowerCase();
                String category = item.path("categoryName").asText("").toLowerCase();
                
                boolean isFood = category.contains("food") || 
                                 category.contains("grocery") ||
                                 name.contains("snack") || 
                                 name.contains("drink") || 
                                 name.contains("beverage");

                if (isFood) {
                    return new ValidationResponse(true, category, "Valid food product found in EAN-Search.");
                } else {
                    return new ValidationResponse(false, category, "Error: Scanned item is a non-consumable product.");
                }
            }
        } catch (RestClientException e) {
            // EAN-Search failed
        }

        // 3. Neither API yielded a result
        return new ValidationResponse(false, "Unknown", "Product not found in any database.");
    }
}