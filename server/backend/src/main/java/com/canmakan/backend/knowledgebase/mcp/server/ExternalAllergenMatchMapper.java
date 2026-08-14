package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.ExternalAllergenMatchPayload;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Maps Tavily grounding text onto one {@link Ingredient} per unresolved label.
 *
 * <p>When AI is enabled, a dedicated ChatClient (no dietary tools) returns JSON.
 * That client is not the Tier-3 evidence agent, so this call cannot recurse into
 * {@code allergen_relationship_lookup}. If AI is off, the ChatClient fails, or
 * search text is empty, mapping falls back to {@link ExternalAllergenMatchParser}
 * or an empty list.
 *
 * @author Amelia
 */
@Slf4j
@Service
public class ExternalAllergenMatchMapper {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ChatClient chatClient;
    private final boolean aiEnabled;

    /**
     * ObjectProvider avoids ChatModel → tool callbacks → mapper → ChatClient → ChatModel.
     * The match client is resolved on first {@link #map} after tools exist.
     */
    @Autowired
    public ExternalAllergenMatchMapper(
            @Qualifier("allergenMatchChatClient") ObjectProvider<ChatClient> chatClientProvider,
            @Value("${canmakan.ai.enabled:false}") boolean aiEnabled) {
        this.chatClientProvider = chatClientProvider;
        this.chatClient = null;
        this.aiEnabled = aiEnabled;
    }

    /**
     * Direct ChatClient for unit tests that do not start the Spring context.
     */
    ExternalAllergenMatchMapper(ChatClient chatClient, boolean aiEnabled) {
        this.chatClientProvider = null;
        this.chatClient = chatClient;
        this.aiEnabled = aiEnabled;
    }

    /**
     * Parser-only mapper for unit tests that do not exercise the ChatClient.
     */
    static ExternalAllergenMatchMapper parserOnly() {
        return new ExternalAllergenMatchMapper((ChatClient) null, false);
    }

    /**
     * @param unresolvedIngredients labels that missed the local catalog
     * @param searchSummary Tavily grounding text; blank skips ChatClient so mappings
     *                      are not invented without web search
     */
    public List<Ingredient> map(List<String> unresolvedIngredients, String searchSummary) {
        if (unresolvedIngredients == null || unresolvedIngredients.isEmpty()
                || searchSummary == null || searchSummary.isBlank()) {
            return List.of();
        }

        ChatClient client = resolveChatClient();
        if (aiEnabled && client != null) {
            try {
                List<Ingredient> fromModel = mapWithChatClient(client, unresolvedIngredients, searchSummary);
                if (!fromModel.isEmpty()) {
                    return fromModel;
                }
            } catch (RuntimeException exception) {
                log.debug(
                    "Structured allergen mapping failed ({}); using regex parser",
                    exception.getClass().getSimpleName());
            }
        }

        return ExternalAllergenMatchParser.parse(unresolvedIngredients, searchSummary);
    }

    private ChatClient resolveChatClient() {
        if (chatClient != null) {
            return chatClient;
        }
        if (chatClientProvider == null) {
            return null;
        }
        return chatClientProvider.getIfAvailable();
    }

    private List<Ingredient> mapWithChatClient(
            ChatClient client,
            List<String> unresolvedIngredients, String searchSummary) {
        ExternalAllergenMatchPayload payload = client.prompt()
            .user(buildPrompt(unresolvedIngredients, searchSummary))
            .call()
            .entity(ExternalAllergenMatchPayload.class);

        if (payload == null || payload.matches() == null) {
            return List.of();
        }

        Map<String, Ingredient> byKey = new LinkedHashMap<>();
        for (ExternalAllergenMatchPayload.Match match : payload.matches()) {
            if (match == null || match.ingredient() == null || match.rootAllergen() == null) {
                continue;
            }
            String unresolved = matchUnresolved(match.ingredient(), unresolvedIngredients);
            String root = ExternalAllergenMatchParser.canonicalRoot(match.rootAllergen());
            if (unresolved == null || root == null) {
                continue;
            }
            byKey.putIfAbsent(
                unresolved.trim().toLowerCase(Locale.ROOT),
                new Ingredient(unresolved, null, root, false));
        }
        return new ArrayList<>(byKey.values());
    }

    private static String buildPrompt(List<String> unresolvedIngredients, String searchSummary) {
        String names = unresolvedIngredients.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .map(name -> "- " + name)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        return """
            Map each unresolved ingredient to a CanMakan root allergen using ONLY the web search text.
            Do not use prior knowledge. If the search text does not support a mapping, use NONE.

            Unresolved ingredients:
            %s

            Web search text:
            %s

            Return JSON only:
            {"matches":[{"ingredient":"...","rootAllergen":"DAIRY"}]}
            rootAllergen must be one of: DAIRY, GLUTEN, PEANUT, TREE_NUT, FISH, SHELLFISH, EGG, SOY, SESAME, MEAT, ADDITIVE, NONE.
            Include at most one row per unresolved ingredient. Do not emit SAFE, WARNING, or UNSAFE.
            """.formatted(names, searchSummary);
    }

    private static String matchUnresolved(String labelFromModel, List<String> unresolvedIngredients) {
        String labelKey = labelFromModel.trim().toLowerCase(Locale.ROOT);
        for (String unresolved : unresolvedIngredients) {
            if (unresolved == null || unresolved.isBlank()) {
                continue;
            }
            String unresolvedKey = unresolved.trim().toLowerCase(Locale.ROOT);
            if (labelKey.equals(unresolvedKey)
                    || labelKey.contains(unresolvedKey)
                    || unresolvedKey.contains(labelKey)) {
                return unresolved.trim();
            }
        }
        return null;
    }
}
