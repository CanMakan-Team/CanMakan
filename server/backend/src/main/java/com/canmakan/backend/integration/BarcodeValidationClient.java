package com.canmakan.backend.integration;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.ValidationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for interacting with the barcode validation service.
 *
 * @author K4i-Z3r
 * @author YangMaowei
 */
@Service
public class BarcodeValidationClient {
    private final RestClient offRestClient;
    private final RestClient eanRestClient;
    private final String eanSearchToken;
    private final ObjectMapper objectMapper;

    public BarcodeValidationClient(
        @Value("${app.api.ean-search.token}") String eanSearchToken,
        @Value("${app.name:CanMakan}") String appName,
        @Value("${app.version:1.0}") String appVersion,
        @Value("${app.contact.email:khairulanwar.kamaruzaman@u.nus.edu}") String contactEmail
    ) {
        // Construct the custom User-Agent string: AppName/Version (ContactEmail)
        String userAgent = String.format("%s/v%s - (%s)", appName, appVersion, contactEmail);

        // Primary: Open Food Facts (Configured with Custom User-Agent)
        RestClient offClient = RestClient.builder()
            .baseUrl("https://world.openfoodfacts.org/api/v3/product/")
            .defaultHeader("User-Agent", userAgent)
            .build();

        // Fallback: EAN-Search (Configured with base domain root)
        RestClient eanClient = RestClient.builder()
            .baseUrl("https://api.ean-search.org")
            .build();

        this.offRestClient = offClient;
        this.eanRestClient = eanClient;
        this.eanSearchToken = eanSearchToken;
        this.objectMapper = new ObjectMapper();
    }

    BarcodeValidationClient(
        RestClient offRestClient,
        RestClient eanRestClient,
        String eanSearchToken,
        ObjectMapper objectMapper
    ) {
        this.offRestClient = Objects.requireNonNull(offRestClient, "offRestClient");
        this.eanRestClient = Objects.requireNonNull(eanRestClient, "eanRestClient");
        this.eanSearchToken = eanSearchToken;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ValidationResponse validateProduct(String barcode) {
        // 1. Primary Lookup: Open Food Facts
        try {
            String offResponseStr = offRestClient.get()
                .uri(barcode + ".json")
                .retrieve()
                .body(String.class);

            if (offResponseStr != null) {
                JsonNode offResponse = objectMapper.readTree(offResponseStr);
                
                if (offResponse.has("status")) {
                    String status = offResponse.get("status").asText();
                    if ("success".equalsIgnoreCase(status) || "1".equals(status)) {
                        JsonNode product = offResponse.get("product");
                        String category = (product != null && product.has("product_type"))
                                 ? product.get("product_type").asText()
                                 : "food";
                        return new ValidationResponse(true, category, "Valid food product found in Open Food Facts.");
                    }
                }
            }
        } catch (Exception e) {
            // Product not found, API error, or parsing error; suppress and proceed to fallback
        }

        // 2. Fallback Lookup: EAN-Search
        try {
            String eanResponseStr = eanRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api")
                    .queryParam("token", eanSearchToken)
                    .queryParam("op", "barcode-lookup")
                    .queryParam("format", "json")
                    .queryParam("ean", barcode)
                    .build())
                .retrieve()
                .body(String.class);

            if (eanResponseStr != null) {
                JsonNode eanResponse = objectMapper.readTree(eanResponseStr);
                
                if (eanResponse.isArray() && !eanResponse.isEmpty()) {
                    JsonNode item = eanResponse.get(0);
                    
                    if (item.has("error")) {
                         return new ValidationResponse(false, "Unknown", "Product not found in fallback database.");
                    }

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
            }
        } catch (Exception e) {
            // EAN-Search failed or parsing error
        }

        // 3. Neither API yielded a result
        return new ValidationResponse(false, "Unknown", "Product not found in any database.");
    }

    /**
     * Fetches the full Open Food Facts product snapshot needed by assessment.
     *
     * @param barcode an 8-to-14 digit product barcode
     * @return source-neutral product data without dietary interpretation
     * @throws ProductLookupException when input or provider data is unusable
     */
    public ProductLookupResult fetchProduct(String barcode) {
        validateFetchBarcode(barcode);

        String responseBody;
        try {
            responseBody = offRestClient.get()
                .uri(barcode + ".json")
                .retrieve()
                .body(String.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw lookupFailure(
                ProductLookupException.Reason.PRODUCT_NOT_FOUND,
                "Product was not found by the provider."
            );
        } catch (HttpClientErrorException exception) {
            throw lookupFailure(
                ProductLookupException.Reason.PROVIDER_FAILURE,
                "Product provider rejected the request."
            );
        } catch (HttpServerErrorException exception) {
            throw lookupFailure(
                ProductLookupException.Reason.TRANSIENT_FAILURE,
                "Product provider is temporarily unavailable."
            );
        } catch (ResourceAccessException exception) {
            throw resourceFailure(exception);
        } catch (RestClientResponseException exception) {
            throw lookupFailure(
                ProductLookupException.Reason.PROVIDER_FAILURE,
                "Product provider returned an unsuccessful response."
            );
        } catch (RestClientException exception) {
            throw lookupFailure(
                ProductLookupException.Reason.PROVIDER_FAILURE,
                "Product provider request failed."
            );
        }

        OpenFoodFactsResponse response = parseFullProductResponse(responseBody);
        if (!response.successful()) {
            throw new ProductLookupException(
                ProductLookupException.Reason.PRODUCT_NOT_FOUND,
                "Product was not found by the provider."
            );
        }
        if (response.product() == null) {
            throw new ProductLookupException(
                ProductLookupException.Reason.MALFORMED_RESPONSE,
                "Product provider response did not contain product data."
            );
        }

        return toProductLookupResult(barcode, response.product());
    }

    private void validateFetchBarcode(String barcode) {
        if (barcode == null || barcode.isBlank() || !barcode.matches("\\d{8,14}")) {
            throw new ProductLookupException(
                ProductLookupException.Reason.INVALID_BARCODE,
                "Barcode must contain 8 to 14 digits."
            );
        }
    }

    private OpenFoodFactsResponse parseFullProductResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ProductLookupException(
                ProductLookupException.Reason.MALFORMED_RESPONSE,
                "Product provider returned an empty response."
            );
        }

        try {
            OpenFoodFactsResponse response = objectMapper.readValue(
                responseBody,
                OpenFoodFactsResponse.class
            );
            if (response == null || response.status() == null || response.status().isBlank()) {
                throw new ProductLookupException(
                    ProductLookupException.Reason.MALFORMED_RESPONSE,
                    "Product provider response did not contain a status."
                );
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw lookupFailure(
                ProductLookupException.Reason.MALFORMED_RESPONSE,
                "Product provider returned malformed data."
            );
        }
    }

    private ProductLookupResult toProductLookupResult(
        String barcode,
        OpenFoodFactsProduct product
    ) {
        List<Ingredient> ingredients = toIngredients(product.ingredients());
        boolean ingredientDataComplete = product.ingredients() != null
            && !product.ingredients().isEmpty()
            && ingredients.size() == product.ingredients().size();

        return new ProductLookupResult(
            barcode,
            product.productName(),
            product.productType(),
            ingredients,
            product.ingredientsText(),
            toLabelTags(product.labelTags()),
            toNutrition(product.nutriments()),
            ingredientDataComplete
        );
    }

    private List<Ingredient> toIngredients(List<OpenFoodFactsIngredient> source) {
        if (source == null) {
            return List.of();
        }

        return source.stream()
            .filter(Objects::nonNull)
            .map(this::ingredientName)
            .filter(Objects::nonNull)
            .map(name -> new Ingredient(name, null, null, false))
            .toList();
    }

    private String ingredientName(OpenFoodFactsIngredient ingredient) {
        if (ingredient.text() != null && !ingredient.text().isBlank()) {
            return ingredient.text();
        }
        if (ingredient.id() != null && !ingredient.id().isBlank()) {
            return ingredient.id();
        }
        return null;
    }

    private String toLabelTags(List<String> labelTags) {
        if (labelTags == null) {
            return null;
        }
        return labelTags.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.joining(","));
    }

    private Nutrition toNutrition(OpenFoodFactsNutriments nutriments) {
        if (nutriments == null) {
            return null;
        }
        return new Nutrition(
            nutriments.sugarsPer100g(),
            nutriments.sodiumPer100g(),
            nutriments.transFatPer100g(),
            nutriments.saturatedFatPer100g(),
            nutriments.fatPer100g(),
            nutriments.energyKcalPer100g()
        );
    }

    private ProductLookupException resourceFailure(ResourceAccessException exception) {
        if (hasCause(exception, InterruptedException.class)) {
            Thread.currentThread().interrupt();
            return lookupFailure(
                ProductLookupException.Reason.INTERRUPTED,
                "Product provider request was interrupted."
            );
        }
        if (hasCause(exception, SocketTimeoutException.class)
            || hasCause(exception, HttpTimeoutException.class)) {
            return lookupFailure(
                ProductLookupException.Reason.TIMEOUT,
                "Product provider request timed out."
            );
        }
        return lookupFailure(
            ProductLookupException.Reason.TRANSIENT_FAILURE,
            "Product provider could not be reached."
        );
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ProductLookupException lookupFailure(
        ProductLookupException.Reason reason,
        String message
    ) {
        return new ProductLookupException(reason, message);
    }
}
