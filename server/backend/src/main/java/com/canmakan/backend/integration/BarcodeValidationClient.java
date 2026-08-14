package com.canmakan.backend.integration;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.ValidationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
 * @author Amelia Wong
 */
@Service
public class BarcodeValidationClient {
    private final RestClient offRestClient;
    private final RestClient eanRestClient;
    private final String eanSearchToken;
    private final ObjectMapper objectMapper;
    private final int retryMaxAttempts;
    private final long retryBackoffMs;
    private final RetrySleeper retrySleeper;

    @Autowired
    public BarcodeValidationClient(
        @Value("${app.api.product.ean-search-token}") String eanSearchToken,
        @Value("${app.name:CanMakan}") String appName,
        @Value("${app.version:1.0}") String appVersion,
        @Value("${app.contact.email:khairulanwar.kamaruzaman@u.nus.edu}") String contactEmail,
        @Value("${app.api.product.open-food-facts-base-url}") String offBaseUrl,
        @Value("${app.api.product.ean-search-base-url}") String eanBaseUrl,
        @Value("${app.api.product.connect-timeout-ms}") long connectTimeoutMs,
        @Value("${app.api.product.response-timeout-ms}") long responseTimeoutMs,
        @Value("${app.api.product.retry.max-attempts}") int retryMaxAttempts,
        @Value("${app.api.product.retry.backoff-ms}") long retryBackoffMs
    ) {
        this(
            createOffRestClient(
                offBaseUrl,
                connectTimeoutMs,
                responseTimeoutMs,
                userAgent(appName, appVersion, contactEmail)
            ),
            createEanRestClient(eanBaseUrl, connectTimeoutMs, responseTimeoutMs),
            eanSearchToken,
            new ObjectMapper(),
            retryMaxAttempts,
            retryBackoffMs,
            Thread::sleep
        );
    }

    BarcodeValidationClient(
        RestClient offRestClient,
        RestClient eanRestClient,
        String eanSearchToken,
        ObjectMapper objectMapper
    ) {
        this(
            offRestClient,
            eanRestClient,
            eanSearchToken,
            objectMapper,
            1,
            0,
            Thread::sleep
        );
    }

    BarcodeValidationClient(
        RestClient offRestClient,
        RestClient eanRestClient,
        String eanSearchToken,
        ObjectMapper objectMapper,
        int retryMaxAttempts,
        long retryBackoffMs,
        RetrySleeper retrySleeper
    ) {
        this.offRestClient = Objects.requireNonNull(offRestClient, "offRestClient");
        this.eanRestClient = Objects.requireNonNull(eanRestClient, "eanRestClient");
        this.eanSearchToken = eanSearchToken;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (retryMaxAttempts < 1) {
            throw new IllegalArgumentException("retryMaxAttempts must be at least 1");
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException("retryBackoffMs must not be negative");
        }
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryBackoffMs = retryBackoffMs;
        this.retrySleeper = Objects.requireNonNull(retrySleeper, "retrySleeper");
    }

    public ValidationResponse validateProduct(String barcode) {
        // 1. Primary Lookup: Open Food Facts
        try {
            String offResponseStr = executeWithRetry(() -> offRestClient.get()
                    .uri(barcode + ".json")
                    .retrieve()
                    .body(String.class));

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
            String eanResponseStr = executeWithRetry(() -> eanRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/api")
                        .queryParam("token", eanSearchToken)
                        .queryParam("op", "barcode-lookup")
                        .queryParam("format", "json")
                        .queryParam("ean", barcode)
                        .build())
                    .retrieve()
                    .body(String.class));

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
            responseBody = executeWithRetry(() -> offRestClient.get()
                    .uri(barcode + ".json")
                    .retrieve()
                    .body(String.class));
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
        List<Ingredient> ingredients = new ArrayList<>(toIngredients(product.ingredients()));
        boolean ingredientDataComplete = product.ingredients() != null
            && !product.ingredients().isEmpty()
            && ingredients.size() == countLeafIngredients(product.ingredients());

        // Open Food Facts publishes curated allergen tags (e.g. "en:peanuts"). Inject each mapped
        // tag as a confirmed allergen so it is flagged even when the ingredient names do not match
        // the catalog (e.g. "Roasted Peanuts" / "Peanut Oil" for a PEANUT restriction).
        addAllergenTagIngredients(product.allergensTags(), ingredients);

        return new ProductLookupResult(
            barcode,
            product.productName(),
            product.productType(),
            List.copyOf(ingredients),
            product.ingredientsText(),
            toLabelTags(product.labelTags()),
            normalizeTracesTags(product.tracesTags()),
            toNutrition(product.nutriments()),
            ingredientDataComplete
        );
    }

    private static List<String> normalizeTracesTags(List<String> tracesTags) {
        if (tracesTags == null || tracesTags.isEmpty()) {
            return List.of();
        }
        return tracesTags.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(tag -> !tag.isEmpty())
            .distinct()
            .toList();
    }

    private List<Ingredient> toIngredients(List<OpenFoodFactsIngredient> source) {
        List<Ingredient> flattened = new ArrayList<>();
        collectLeafIngredients(source, flattened);
        return List.copyOf(flattened);
    }

    /**
     * Open Food Facts nests compound ingredients (e.g. "Cereals Grains" -> Wheat, Oat Flakes;
     * "Raising Agent" -> Malted Barley). The allergen sources live in the leaves, so recurse into
     * any nested list and keep only leaf ingredients; a node without a nested list is itself a leaf.
     */
    private void collectLeafIngredients(
            List<OpenFoodFactsIngredient> source, List<Ingredient> out) {
        if (source == null) {
            return;
        }
        for (OpenFoodFactsIngredient node : source) {
            if (node == null) {
                continue;
            }
            if (node.ingredients() != null && !node.ingredients().isEmpty()) {
                collectLeafIngredients(node.ingredients(), out);
                continue;
            }
            String name = ingredientName(node);
            if (name != null) {
                out.add(new Ingredient(name, null, null, false));
            }
        }
    }

    // Maps an Open Food Facts allergen tag (language prefix stripped, e.g. "peanuts") to a
    // CanMakan root allergen code understood by the verdict engine.
    private static final Map<String, String> OFF_ALLERGEN_ROOTS = Map.ofEntries(
        Map.entry("peanuts", "PEANUT"),
        Map.entry("milk", "DAIRY"),
        Map.entry("gluten", "GLUTEN"),
        Map.entry("soybeans", "SOY"),
        Map.entry("soya", "SOY"),
        Map.entry("eggs", "EGG"),
        Map.entry("nuts", "TREE_NUT"),
        Map.entry("tree-nuts", "TREE_NUT"),
        Map.entry("fish", "FISH"),
        Map.entry("crustaceans", "SHELLFISH"),
        Map.entry("molluscs", "SHELLFISH"),
        Map.entry("sesame-seeds", "SESAME"),
        Map.entry("sesame", "SESAME"),
        Map.entry("sulphur-dioxide-and-sulphites", "ADDITIVE")
    );

    private static final Pattern OFF_TAG_LANG_PREFIX = Pattern.compile("^[a-z]{2,3}:");

    /**
     * Turns Open Food Facts allergen tags into confirmed-allergen ingredients (root already set)
     * so the verdict engine flags them directly, regardless of how the ingredient list is worded.
     * Deduplicates by root so a product is not tagged with the same allergen twice.
     */
    private void addAllergenTagIngredients(List<String> allergenTags, List<Ingredient> out) {
        if (allergenTags == null) {
            return;
        }
        Set<String> seenRoots = new HashSet<>();
        for (String tag : allergenTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String key = OFF_TAG_LANG_PREFIX
                .matcher(tag.trim().toLowerCase(Locale.ROOT)).replaceAll("");
            String root = OFF_ALLERGEN_ROOTS.get(key);
            if (root == null || !seenRoots.add(root)) {
                continue;
            }
            out.add(new Ingredient(key.replace('-', ' ') + " (declared allergen)", null, root, false));
        }
    }

    /** Counts the leaf ingredients OFF supplied, so completeness reflects nested groups too. */
    private int countLeafIngredients(List<OpenFoodFactsIngredient> source) {
        if (source == null) {
            return 0;
        }
        int count = 0;
        for (OpenFoodFactsIngredient node : source) {
            if (node == null) {
                continue;
            }
            if (node.ingredients() != null && !node.ingredients().isEmpty()) {
                count += countLeafIngredients(node.ingredients());
            } else {
                count++;
            }
        }
        return count;
    }

    // Open Food Facts ingredient ids for additives are the bare E-code, e.g. "en:e200".
    private static final Pattern OFF_ENUMBER_ID =
        Pattern.compile("^en:(e\\d{3,4}[a-z]?)$", Pattern.CASE_INSENSITIVE);
    // Matches an E-number already present in the human-readable text, e.g. "E 200".
    private static final Pattern TEXT_HAS_ENUMBER =
        Pattern.compile("(?i)\\bE\\s*-?\\s*\\d{3,4}");

    private String ingredientName(OpenFoodFactsIngredient ingredient) {
        String text = ingredient.text();
        String eCode = eNumberFromId(ingredient.id());
        if (text != null && !text.isBlank()) {
            // Open Food Facts often carries a plain additive name in text ("Sorbic acid") while the
            // E-number lives only in the id ("en:e200"). Surface that E-number so the deterministic
            // E-number resolver can classify the additive, instead of leaving it unresolved and
            // falling through to the LLM (which may guess a wrong allergen).
            if (eCode != null && !TEXT_HAS_ENUMBER.matcher(text).find()) {
                return text.trim() + " (" + eCode + ")";
            }
            return text;
        }
        if (ingredient.id() != null && !ingredient.id().isBlank()) {
            return eCode != null ? eCode : ingredient.id();
        }
        return null;
    }

    /** Returns the upper-case E-code (e.g. {@code E200}) when the OFF id is an additive id. */
    private static String eNumberFromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Matcher matcher = OFF_ENUMBER_ID.matcher(id.trim());
        return matcher.matches() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
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

    private <T> T executeWithRetry(Supplier<T> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request.get();
            } catch (RestClientException exception) {
                if (attempt >= retryMaxAttempts || !isRetryable(exception)) {
                    throw exception;
                }
                waitBeforeRetry();
            }
        }
    }

    private boolean isRetryable(RestClientException exception) {
        return exception instanceof HttpServerErrorException
            || exception instanceof ResourceAccessException;
    }

    private void waitBeforeRetry() {
        if (retryBackoffMs == 0) {
            return;
        }
        try {
            retrySleeper.sleep(retryBackoffMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException(
                "Product provider retry was interrupted.",
                new IOException("Retry backoff was interrupted.", exception)
            );
        }
    }

    private static RestClient createOffRestClient(
        String baseUrl,
        long connectTimeoutMs,
        long responseTimeoutMs,
        String userAgent
    ) {
        return configuredRestClientBuilder(
                ensureTrailingSlash(baseUrl),
                connectTimeoutMs,
                responseTimeoutMs
            )
            .defaultHeader("User-Agent", userAgent)
            .build();
    }

    private static RestClient createEanRestClient(
        String baseUrl,
        long connectTimeoutMs,
        long responseTimeoutMs
    ) {
        return configuredRestClientBuilder(
                removeTrailingSlash(baseUrl),
                connectTimeoutMs,
                responseTimeoutMs
            )
            .build();
    }

    static RestClient.Builder configuredRestClientBuilder(
        String baseUrl,
        long connectTimeoutMs,
        long responseTimeoutMs
    ) {
        String normalizedBaseUrl = requireBaseUrl(baseUrl);
        return RestClient.builder()
            .baseUrl(normalizedBaseUrl)
            .requestFactory(createRequestFactory(connectTimeoutMs, responseTimeoutMs));
    }

    static SimpleClientHttpRequestFactory createRequestFactory(
        long connectTimeoutMs,
        long responseTimeoutMs
    ) {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectTimeoutMs must be positive");
        }
        if (responseTimeoutMs <= 0) {
            throw new IllegalArgumentException("responseTimeoutMs must be positive");
        }

        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(responseTimeoutMs));
        return requestFactory;
    }

    private static String userAgent(String appName, String appVersion, String contactEmail) {
        return String.format("%s/v%s - (%s)", appName, appVersion, contactEmail);
    }

    private static String ensureTrailingSlash(String baseUrl) {
        String normalizedBaseUrl = requireBaseUrl(baseUrl);
        return normalizedBaseUrl.endsWith("/")
            ? normalizedBaseUrl
            : normalizedBaseUrl + "/";
    }

    private static String removeTrailingSlash(String baseUrl) {
        String normalizedBaseUrl = requireBaseUrl(baseUrl);
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl;
    }

    private static String requireBaseUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return baseUrl.trim();
    }
}
