package com.canmakan.backend.knowledgebase.mcp.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fallback strategy for allergen hierarchy lookups that cannot be resolved locally.
 * Implementations can delegate to an external web-search provider 
 * 
 * @author Amelia
 */
@Slf4j
@Service
public class AllergenRelationshipLookupFallback {

    private final WebClient.Builder webClientBuilder; // Inject web client builder

    public AllergenRelationshipLookupFallback() {
        this(null);
    }

    public AllergenRelationshipLookupFallback(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // @Value("${app.api.tavily.key}")
    // private String tavilyApiKey;

    // @Value("${app.api.tavily.url}")
    // private String tavilyUrl;

    @Value("${TAVILY_API_KEY:mock_key_value}")
    private String tavilyApiKey;

    @Value("${TAVILY_API_URL:https://tavily.com}")
    private String tavilyApiUrl;


    /**
     * Resolve parent/root allergens by querying an external search tool
     *
     * @param ingredients the queried ingredients from the scanned product label
     * @return a human-readable fallback description, or null when unavailable
     */
    public String searchExternal(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) { return null; }

        // Normalize list of ingredients:
        //  - Null/blank filter
        //  - Trim whitespace
        //  - Distinct terms
        List<String> normalized = ingredients.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());

        if (normalized.isEmpty()) { return null; }

        // Build query from normalized list
        String query = buildSearchQuery(normalized);

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
                .block();

            return extractAnswer(response, normalized);

        } catch (Exception e) {
            log.warn("Tavily search failed for ingredients {}: {}", normalized, e.getMessage());
            return "External lookup failed for unresolved ingredients: " + String.join(", ", normalized);
        }

    }

    /**
     * Convenience overload for a single comma-separated query string.
     */
    public String searchExternal(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) { return null; }

        List<String> ingredients = Arrays.stream(ingredientText.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toList());

        return searchExternal(ingredients);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildSearchQuery(List<String> ingredients) {
        // Focused query so Tavily returns allergen hierarchy / parent allergen information
        return "What is the parent allergen or root allergen of these food ingredients: "
                + String.join(", ", ingredients)
                + "? List the most common allergen family for each.";
    }

    @SuppressWarnings("unchecked")
    private String extractAnswer(Map<String, Object> response, List<String> ingredients) {
        if (response == null) { return null; }

        // Prefer the summarized "answer" field that Tavily provides
        Object answer = response.get("answer");
        if (answer != null && !answer.toString().isBlank()) {
            return answer.toString().trim();
        }

        // Fallback: concatenate the top results
        Object resultsObj = response.get("results");
        if (!(resultsObj instanceof List<?> rawResults) || rawResults.isEmpty()) {
            return "No external information found for: " + String.join(", ", ingredients);
        }

        // Limit 3 results
        // For each, parse title + content
        // Concat in order
        return rawResults.stream()
            .limit(3)
            .filter(Map.class::isInstance)// filter map instance only
            .map(r -> (Map<String, Object>) r) // Map to String, Object first
            .map(r -> {
                String title = String.valueOf(r.getOrDefault("title", ""));
                String content = String.valueOf(r.getOrDefault("content", ""));
                return title + ": " + content;
            })
            .collect(Collectors.joining("\n\n"));
    }

}
