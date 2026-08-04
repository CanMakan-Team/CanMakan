package com.canmakan.backend.integration;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.ValidationResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Retrieves product data from Open Food Facts and preserves the existing
 * validation fallback to EAN-Search.
 *
 * @author YangMaowei
 */
@Service
public class BarcodeValidationClient {

    private static final String OFF_FIELDS = String.join(",",
            "product_name",
            "product_type",
            "ingredients_text",
            "ingredients",
            "labels_tags",
            "nutriments"
    );

    private final RestClient offRestClient;
    private final RestClient eanRestClient;
    private final String eanSearchToken;
    private final int retryCount;
    private final Duration retryBackoff;

    public BarcodeValidationClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.api.ean-search.token:}") String eanSearchToken,
            @Value("${app.integration.open-food-facts.base-url:https://world.openfoodfacts.org/api/v3/product}")
            String offBaseUrl,
            @Value("${app.integration.ean-search.base-url:https://api.ean-search.org}")
            String eanBaseUrl,
            @Value("${app.integration.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.integration.response-timeout:8s}") Duration responseTimeout,
            @Value("${app.integration.retry-count:2}") int retryCount,
            @Value("${app.integration.retry-backoff:200ms}") Duration retryBackoff,
            @Value("${app.name:CanMakan}") String appName,
            @Value("${app.version:1.0}") String appVersion,
            @Value("${app.contact.email:canmakan@example.invalid}") String contactEmail
    ) {
        this(
                buildClient(
                        restClientBuilder.clone(),
                        offBaseUrl,
                        connectTimeout,
                        responseTimeout,
                        appName + "/v" + appVersion + " (" + contactEmail + ")"
                ),
                buildClient(
                        restClientBuilder.clone(),
                        eanBaseUrl,
                        connectTimeout,
                        responseTimeout,
                        null
                ),
                eanSearchToken,
                retryCount,
                retryBackoff
        );
    }

    BarcodeValidationClient(
            RestClient offRestClient,
            RestClient eanRestClient,
            String eanSearchToken,
            int retryCount,
            Duration retryBackoff
    ) {
        this.offRestClient = Objects.requireNonNull(offRestClient, "offRestClient");
        this.eanRestClient = Objects.requireNonNull(eanRestClient, "eanRestClient");
        this.eanSearchToken = eanSearchToken == null ? "" : eanSearchToken;
        this.retryCount = Math.max(0, retryCount);
        this.retryBackoff = retryBackoff == null || retryBackoff.isNegative()
                ? Duration.ZERO
                : retryBackoff;
    }

    /**
     * Retrieves the complete source-neutral product snapshot used by the rule engine.
     */
    public Optional<ProductLookupResult> fetchProduct(String barcode) {
        String validBarcode = requireBarcode(barcode);
        return fetchOpenFoodFactsProduct(validBarcode)
                .map(product -> mapProduct(validBarcode, product));
    }

    /**
     * Preserves the validation-only contract used by the scan validation endpoint.
     */
    public ValidationResponse validateProduct(String barcode) {
        String validBarcode = requireBarcode(barcode);
        try {
            Optional<OpenFoodFactsProduct> offProduct = fetchOpenFoodFactsProduct(validBarcode);
            if (offProduct.isPresent()) {
                String category = defaultIfBlank(offProduct.get().productType(), "food");
                return new ValidationResponse(
                        true,
                        category,
                        "Valid food product found in Open Food Facts."
                );
            }
        } catch (ProductLookupException ignored) {
            // The existing validation flow continues to its secondary provider.
        }

        return validateWithEanSearch(validBarcode);
    }

    private Optional<OpenFoodFactsProduct> fetchOpenFoodFactsProduct(String barcode) {
        OpenFoodFactsResponse response;
        try {
            response = executeWithRetry(() -> offRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(barcode + ".json")
                            .queryParam("fields", OFF_FIELDS)
                            .build())
                    .retrieve()
                    .body(OpenFoodFactsResponse.class));
        } catch (HttpClientErrorException.NotFound ignored) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new ProductLookupException(
                    ProductLookupException.Reason.REMOTE_FAILURE,
                    "Open Food Facts lookup failed.",
                    exception
            );
        }

        if (response == null || !response.successful()) {
            return Optional.empty();
        }
        if (response.product() == null || isBlank(response.product().productName())) {
            throw new ProductLookupException(
                    ProductLookupException.Reason.INVALID_RESPONSE,
                    "Open Food Facts returned an incomplete product response."
            );
        }
        return Optional.of(response.product());
    }

    private ValidationResponse validateWithEanSearch(String barcode) {
        try {
            EanSearchItem[] response = executeWithRetry(() -> eanRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api")
                            .queryParam("token", eanSearchToken)
                            .queryParam("op", "barcode-lookup")
                            .queryParam("format", "json")
                            .queryParam("ean", barcode)
                            .build())
                    .retrieve()
                    .body(EanSearchItem[].class));

            if (response != null && response.length > 0 && isBlank(response[0].error())) {
                EanSearchItem item = response[0];
                String name = defaultIfBlank(item.name(), "").toLowerCase();
                String category = defaultIfBlank(item.categoryName(), "").toLowerCase();
                boolean food = category.contains("food")
                        || category.contains("grocery")
                        || name.contains("snack")
                        || name.contains("drink")
                        || name.contains("beverage");
                if (food) {
                    return new ValidationResponse(
                            true,
                            category,
                            "Valid food product found in EAN-Search."
                    );
                }
                return new ValidationResponse(
                        false,
                        category,
                        "Error: Scanned item is a non-consumable product."
                );
            }
        } catch (RestClientException | ProductLookupException ignored) {
            // Validation returns a controlled not-found response below.
        }

        return new ValidationResponse(
                false,
                "Unknown",
                "Product not found in any database."
        );
    }

    private ProductLookupResult mapProduct(String barcode, OpenFoodFactsProduct product) {
        List<Ingredient> ingredients = mapIngredients(product.ingredients());
        boolean ingredientDataComplete = !isBlank(product.ingredientsText())
                && product.ingredients() != null
                && !product.ingredients().isEmpty()
                && ingredients.size() == product.ingredients().size();
        String labelTags = product.labelTags() == null
                ? null
                : String.join(",", product.labelTags());

        return new ProductLookupResult(
                barcode,
                product.productName(),
                product.productType(),
                ingredients,
                product.ingredientsText(),
                labelTags,
                mapNutrition(product.nutriments()),
                ingredientDataComplete
        );
    }

    private List<Ingredient> mapIngredients(List<OpenFoodFactsIngredient> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<Ingredient> ingredients = new ArrayList<>();
        for (OpenFoodFactsIngredient ingredient : source) {
            if (ingredient == null) {
                continue;
            }
            String name = defaultIfBlank(ingredient.text(), ingredient.id());
            if (!isBlank(name)) {
                ingredients.add(new Ingredient(name.trim(), null, null, false));
            }
        }
        return List.copyOf(ingredients);
    }

    private Nutrition mapNutrition(OpenFoodFactsNutriments nutriments) {
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

    private <T> T executeWithRetry(Supplier<T> request) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                return request.get();
            } catch (RuntimeException exception) {
                if (!isTransient(exception) || attempt == retryCount) {
                    throw exception;
                }
                lastFailure = exception;
                pauseBeforeRetry(attempt + 1);
            }
        }
        throw lastFailure;
    }

    private boolean isTransient(RuntimeException exception) {
        return exception instanceof ResourceAccessException
                || exception instanceof HttpServerErrorException
                || exception instanceof HttpClientErrorException.TooManyRequests;
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBackoff.multipliedBy(attempt).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProductLookupException(
                    ProductLookupException.Reason.INTERRUPTED,
                    "Product lookup retry was interrupted.",
                    exception
            );
        }
    }

    private static RestClient buildClient(
            RestClient.Builder builder,
            String baseUrl,
            Duration connectTimeout,
            Duration responseTimeout,
            String userAgent
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(responseTimeout);
        builder.baseUrl(baseUrl).requestFactory(requestFactory);
        if (!isBlank(userAgent)) {
            builder.defaultHeader("User-Agent", userAgent);
        }
        return builder.build();
    }

    private String requireBarcode(String barcode) {
        if (isBlank(barcode)) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        return barcode.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
