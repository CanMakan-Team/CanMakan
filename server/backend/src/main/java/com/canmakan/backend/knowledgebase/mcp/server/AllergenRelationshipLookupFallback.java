package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.model.IngredientLabelParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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
    /** Upper bound on unresolved names included in the single Tavily query. */
    private static final int MAX_EXTERNAL_INGREDIENTS = 8;
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

    /**
     * Resolve parent/root allergens by querying an external search tool.
     *
     * Issues one Tavily search covering the unresolved list (capped). The returned
     * text is grounding for structured mapping, not a verdict.
     *
     * @param ingredients the queried ingredients from the scanned product label
     * @return a human-readable fallback description, or {@code ""} when unavailable
     */
    public String searchExternal(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return "";
        }

        List<String> normalized = IngredientLabelParser.normalize(ingredients).stream()
            .distinct()
            .collect(Collectors.toList());

        if (normalized.isEmpty()) {
            return "";
        }

        if (!isConfiguredApiKey(tavilyApiKey)) {
            log.info("Tavily API key not configured; skipping external allergen lookup");
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

        List<String> capped = normalized.size() > MAX_EXTERNAL_INGREDIENTS
            ? List.copyOf(normalized.subList(0, MAX_EXTERNAL_INGREDIENTS))
            : List.copyOf(normalized);
        if (normalized.size() > MAX_EXTERNAL_INGREDIENTS) {
            log.info(
                "Tavily lookup capped unresolved ingredients from {} to {}",
                normalized.size(),
                MAX_EXTERNAL_INGREDIENTS);
        }

        return searchOnce(capped);
    }

    /**
     * One Tavily search for the capped unresolved list. Returns grounding text
     * (answer plus a few source snippets), or {@code ""} on failure.
     */
    private String searchOnce(List<String> ingredients) {
        log.info("Calling Tavily search for {} unresolved ingredient(s): {}", ingredients.size(), ingredients);
        try {
            Map<String, Object> requestBody = Map.of(
                "api_key", tavilyApiKey,
                "query", buildSearchQuery(ingredients),
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

            String grounding = extractGrounding(response);
            log.info(
                "Tavily search returned {} character(s) of grounding text for ingredients {}",
                grounding.length(),
                ingredients);
            return grounding;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == TAVILY_PLAN_LIMIT_STATUS) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily search failed for ingredients {}: {}", ingredients, e.getMessage());
            return "";
        } catch (Exception e) {
            if (isPlanLimitFailure(e)) {
                tavilyPlanLimitReached = true;
                log.warn("Tavily plan limit exceeded (HTTP 432); skipping remaining lookups");
                return "";
            }
            log.warn("Tavily search failed for ingredients {}: {}", ingredients, e.getMessage());
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

        String trimmedName = productName.trim();
        log.info("Calling Tavily product-name allergen lookup for \"{}\"", trimmedName);

        String query = "List the common food allergens present in the product \""
            + trimmedName + "\". Reply with only root codes from this set: "
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
            String text = answer == null ? "" : answer.toString().trim();
            log.info(
                "Tavily product-name lookup for \"{}\" returned {} character(s)",
                trimmedName,
                text.length());
            return text;
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

    private String buildSearchQuery(List<String> ingredients) {
        return "Food allergen derivation for these ingredients: "
            + String.join(", ", ingredients)
            + ". For each ingredient, say whether it comes from milk, gluten, wheat, peanut, "
            + "tree nuts, egg, soy, fish, shellfish or sesame. If unknown, say unknown.";
    }

    /**
     * Prefer Tavily's answer, then a few result snippets so the structured mapper has
     * grounded text. An empty payload contributes nothing.
     */
    private String extractGrounding(Map<String, Object> response) {
        if (response == null) {
            return "";
        }

        String answer = extractAnswer(response);
        String sources = extractSources(response);
        if (answer.isBlank()) {
            return sources;
        }
        if (sources.isBlank()) {
            return answer;
        }
        return "Answer:\n" + answer + "\n\nSources:\n" + sources;
    }

    private String extractAnswer(Map<String, Object> response) {
        Object answer = response.get("answer");
        return answer == null ? "" : answer.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String extractSources(Map<String, Object> response) {
        Object resultsObj = response.get("results");
        if (!(resultsObj instanceof List<?> rawResults) || rawResults.isEmpty()) {
            return "";
        }
        return rawResults.stream()
            .limit(3)
            .filter(Map.class::isInstance)
            .map(row -> (Map<String, Object>) row)
            .map(row -> {
                String title = String.valueOf(row.getOrDefault("title", "")).trim();
                String content = String.valueOf(row.getOrDefault("content", "")).trim();
                if (title.isEmpty() && content.isEmpty()) {
                    return "";
                }
                if (title.isEmpty()) {
                    return content;
                }
                if (content.isEmpty()) {
                    return title;
                }
                return title + ": " + content;
            })
            .filter(line -> !line.isBlank())
            .collect(Collectors.joining("\n"));
    }
}
