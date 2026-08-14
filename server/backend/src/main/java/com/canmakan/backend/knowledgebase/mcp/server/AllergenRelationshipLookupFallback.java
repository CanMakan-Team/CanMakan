package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.model.IngredientLabelParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fallback strategy for allergen hierarchy lookups that cannot be resolved locally.
 * Delegates to an external web-search provider (Tavily) when a real API key is configured.
 *
 * Returns an empty string when the lookup is skipped or unavailable so callers can
 * treat {@code externalSearchSummary} as non-null
 *
 * @author Amelia
 */
@Slf4j
@Service
public class AllergenRelationshipLookupFallback {

    private static final Duration TAVILY_TIMEOUT = Duration.ofSeconds(10);
    /** Upper bound on external calls per scan now that calls scale with ingredient count. */
    private static final int MAX_EXTERNAL_LOOKUPS = 5;
    /** Tavily returns HTTP 432 when the account's plan usage quota is exhausted. */
    private static final int TAVILY_PLAN_LIMIT_STATUS = 432;

    private final WebClient.Builder webClientBuilder;
    @Value("${app.api.tavily.key:${TAVILY_API_KEY:mock_key_value}}")
    private final String tavilyApiKey;
    @Value("${app.api.tavily.url:${TAVILY_API_URL:https://tavily.com}}")
    private final String tavilyUrl;
    /** Once Tavily reports a plan-limit 432, further calls fail the same way until restart. */
    private volatile boolean tavilyPlanLimitReached;

    public AllergenRelationshipLookupFallback(
            WebClient.Builder webClientBuilder,
            @Value("${app.api.tavily.key}") String tavilyApiKey,
            @Value("${app.api.tavily.url}") String tavilyUrl) {
        this.webClientBuilder = webClientBuilder;
        this.tavilyApiKey = tavilyApiKey;
        this.tavilyUrl = tavilyUrl;
    }


// private String tavilyApiKey;
// private String tavilyUrl;
    /**
     * Resolve parent/root allergens by querying an external search tool.
     *
     * @param ingredients the queried ingredients from the scanned product label
     * @return a human-readable fallback description, or {@code ""} when unavailable
     */
    public String searchExternal(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return "";
        }

        // Normalize the ingredients list by trimming and removing duplicates
        List<String> normalized = IngredientLabelParser.normalize(ingredients).stream()
            .distinct()
            .collect(Collectors.toList());

        if (normalized.isEmpty()) {
            return "";
        }

        if (!isConfiguredApiKey(tavilyApiKey)) {
            log.debug("Tavily API key not configured; skipping external allergen lookup");
            return "";
        }

        if (webClientBuilder == null) {
            log.warn("WebClient.Builder not available; skipping Tavily lookup");
            return "";
        }

        if (tavilyPlanLimitReached) {
            log.debug("Tavily plan limit already reached; skipping external allergen lookup");
            return "";
        }

        // A search API cannot follow a strict 'Name -> ROOT_CODE' instruction, and one answer
        // cannot cover many ingredients, so issue one natural-language search per ingredient.
        // Capped by MAX_EXTERNAL_LOOKUPS to bound credit use now that calls scale with count.
        List<String> lines = new ArrayList<>();
        int lookups = 0;
        for (String ingredient : normalized) {
            if (lookups >= MAX_EXTERNAL_LOOKUPS) {
                break;
            }
            lookups++;
            String answer = searchOne(ingredient);
            if (tavilyPlanLimitReached) {
                break;
            }
            if (answer != null && !answer.isBlank()) {
                // One line per ingredient; flatten newlines so the per-ingredient structure holds.
                lines.add(ingredient + ": " + answer.replaceAll("\\s+", " ").trim());
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Runs a single Tavily search for one ingredient. Returns the answer text, or {@code ""} on any
     * failure or empty answer, so a failed lookup contributes nothing to the summary.
     */
    private String searchOne(String ingredient) {
        try {
            Map<String, Object> requestBody = Map.of(
                "api_key", tavilyApiKey,
                "query", buildSearchQuery(ingredient),
                "search_depth", "basic",
                "include_answer", true,
                "max_results", 5
            );

            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(tavilyUrl)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(TAVILY_TIMEOUT);

            return extractAnswer(response);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == TAVILY_PLAN_LIMIT_STATUS) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily search failed for ingredient {}: {}", ingredient, e.getMessage());
            return "";
        } catch (Exception e) {
            if (isPlanLimitFailure(e)) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily search failed for ingredient {}: {}", ingredient, e.getMessage());
            return "";
        }
    }

    /**
     * Ask the external search provider which common allergens a product contains, keyed on the
     * product name. Used as a last-resort fallback when the barcode lookup returned no usable
     * ingredient or allergen data. Returns the raw answer text, or {@code ""} when unavailable.
     *
     * @param productName the scanned product's name
     * @return the provider's free-text answer, or {@code ""} when the lookup is skipped/unavailable
     */
    public String searchProductAllergens(String productName) {
        if (productName == null || productName.isBlank()
                || !isConfiguredApiKey(tavilyApiKey) || webClientBuilder == null
                || tavilyPlanLimitReached) {
            return "";
        }

        String query = "List the common food allergens present in the product \""
            + productName.trim() + "\". Reply with only root codes from this set: "
            + "DAIRY, GLUTEN, PEANUT, TREE_NUT, FISH, SHELLFISH, EGG, SOY, SESAME. "
            + "If none are known, reply NONE.";

        try {
            Map<String, Object> requestBody = Map.of(
                "api_key", tavilyApiKey,
                "query", query,
                "search_depth", "basic",
                "include_answer", true,
                "max_results", 5
            );

            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(tavilyUrl)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(TAVILY_TIMEOUT);

            Object answer = response == null ? null : response.get("answer");
            return answer == null ? "" : answer.toString().trim();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == TAVILY_PLAN_LIMIT_STATUS) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily product-name allergen lookup failed for {}: {}", productName, e.getMessage());
            return "";
        } catch (Exception e) {
            if (isPlanLimitFailure(e)) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily product-name allergen lookup failed for {}: {}", productName, e.getMessage());
            return "";
        }
    }

    /**
     * Convenience overload for a single comma-separated query string.
     */
    public String searchExternal(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) {
            return "";
        }

        return searchExternal(IngredientLabelParser.split(ingredientText));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isPlanLimitFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException
                    && responseException.getStatusCode().value() == TAVILY_PLAN_LIMIT_STATUS) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("status code [432]")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isConfiguredApiKey(String apiKey) {
        return apiKey != null
            && !apiKey.isBlank()
            && !"local-dev-placeholder".equals(apiKey);
    }

    // Natural-language allergen question for one ingredient (a search API answers prose, not codes).
    private String buildSearchQuery(String ingredient) {
        return "is " + ingredient + " derived from milk, gluten, wheat, peanut, tree nuts, "
            + "egg, soy, fish, shellfish or sesame";
    }

    // Parse only Tavily's answer field. Raw result snippets contain arbitrary allergen words and are
    // a false-positive source, so they are ignored; an absent answer contributes nothing.
    private String extractAnswer(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object answer = response.get("answer");
        return answer == null ? "" : answer.toString().trim();
    }
}
