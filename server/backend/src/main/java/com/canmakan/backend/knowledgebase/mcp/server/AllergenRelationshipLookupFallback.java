package com.canmakan.backend.knowledgebase.mcp.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
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

    private final WebClient.Builder webClientBuilder;
    private final String tavilyApiKey;
    private final String tavilyUrl;

    public AllergenRelationshipLookupFallback(
            WebClient.Builder webClientBuilder,
            @Value("${app.api.tavily.key}") String tavilyApiKey,
            @Value("${app.api.tavily.url}") String tavilyUrl) {
        this.webClientBuilder = webClientBuilder;
        this.tavilyApiKey = tavilyApiKey;
        this.tavilyUrl = tavilyUrl;
    }

@Value("${app.api.tavily.key:${TAVILY_API_KEY:mock_key_value}}")
private String tavilyApiKey;

@Value("${app.api.tavily.url:${TAVILY_API_URL:https://tavily.com}}")
private String tavilyUrl;
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
        List<String> normalized = ingredients.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
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

        String query = buildSearchQuery(normalized);

        try {
            // Construct the request body for the Tavily API
            Map<String, Object> requestBody = Map.of(
                "api_key", tavilyApiKey,
                "query", query,
                "search_depth", "basic",
                "include_answer", true,
                "max_results", 5
            );

            // Send the request to the Tavily API and block until the response is received
            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(tavilyUrl)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(TAVILY_TIMEOUT);

            // Extract the answer from the response
            String answer = extractAnswer(response, normalized);
            return answer == null ? "" : answer;

        } catch (Exception e) {
            log.warn("Tavily search failed for ingredients {}: {}", normalized, e.getMessage());
            return "External lookup failed for unresolved ingredients: " + String.join(", ", normalized);
        }
    }

    /**
     * Convenience overload for a single comma-separated query string.
     */
    public String searchExternal(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) {
            return "";
        }

        List<String> ingredients = Arrays.stream(ingredientText.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toList());

        return searchExternal(ingredients);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isConfiguredApiKey(String apiKey) {
        return apiKey != null
            && !apiKey.isBlank()
            && !"local-dev-placeholder".equals(apiKey);
    }

    private String buildSearchQuery(List<String> ingredients) {
        return "What is the parent allergen or root allergen of these food ingredients: "
            + String.join(", ", ingredients)
            + "? List the most common allergen family for each.";
    }

    @SuppressWarnings("unchecked")
    private String extractAnswer(Map<String, Object> response, List<String> ingredients) {
        if (response == null) {
            return "";
        }

        Object answer = response.get("answer");
        if (answer != null && !answer.toString().isBlank()) {
            return answer.toString().trim();
        }

        Object resultsObj = response.get("results");
        if (!(resultsObj instanceof List<?> rawResults) || rawResults.isEmpty()) {
            return "No external information found for: " + String.join(", ", ingredients);
        }

        return rawResults.stream()
            .limit(3)
            .filter(Map.class::isInstance)
            .map(r -> (Map<String, Object>) r)
            .map(r -> {
                String title = String.valueOf(r.getOrDefault("title", ""));
                String content = String.valueOf(r.getOrDefault("content", ""));
                return title + ": " + content;
            })
            .collect(Collectors.joining("\n\n"));
    }
}
