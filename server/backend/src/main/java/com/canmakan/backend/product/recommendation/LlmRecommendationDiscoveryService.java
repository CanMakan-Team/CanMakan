package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.verdict.RestrictionRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tier B: LLM suggests substitute barcodes; only catalog rows that exist are returned.
 * DietaryRuleEngine verification happens in {@link RecommendationService}.
 */
@Slf4j
@Service
public class LlmRecommendationDiscoveryService {

    private final ChatClient chatClient;
    private final CatalogProductRepository catalogProductRepository;
    private final RecommendationLogService recommendationLogService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String modelId;

    public LlmRecommendationDiscoveryService(
            @Qualifier("recommendationDiscoveryChatClient") ChatClient chatClient,
            CatalogProductRepository catalogProductRepository,
            RecommendationLogService recommendationLogService,
            ObjectMapper objectMapper,
            @Value("${canmakan.recommendation.llm.enabled:false}") boolean enabled,
            @Value("${spring.ai.openai.chat.model:gpt-4o-mini}") String modelId) {
        this.chatClient = chatClient;
        this.catalogProductRepository = catalogProductRepository;
        this.recommendationLogService = recommendationLogService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.modelId = modelId;
    }

    boolean isEnabled() {
        return enabled;
    }

    List<CatalogProduct> discoverCandidates(
            RecommendationRequest request,
            CatalogProduct source,
            List<RestrictionRule> rules) {
        if (!enabled || request == null || source == null) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        String prompt = buildPrompt(source, rules);
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String rawJson = extractContent(chatResponse);
            LlmRecommendationCandidatePayload payload = parsePayload(rawJson);
            DiscoveryResolution resolution = resolveCandidates(source, payload);

            recordAudit(request, source, resolution, chatResponse, startedAt);
            return resolution.catalogProducts();
        } catch (Exception exception) {
            log.warn(
                    "Tier B recommendation discovery skipped for profileId={}, sourceBarcode={}, reason={}",
                    request.profileId(),
                    source.getBarcode(),
                    exception.getMessage());
            return List.of();
        }
    }

    private static String buildPrompt(CatalogProduct source, List<RestrictionRule> rules) {
        String restrictions = rules == null
                ? ""
                : rules.stream()
                        .map(rule -> rule.code() + "(" + rule.severity() + ")")
                        .collect(Collectors.joining(", "));

        return """
                Scanned product:
                - barcode: %s
                - name: %s
                - brand: %s
                - category: %s
                - category_tags: %s
                - allergens: %s
                - ingredients: %s

                Profile restrictions: %s

                Suggest substitute products suitable for this profile.
                """.formatted(
                nullToEmpty(source.getBarcode()),
                nullToEmpty(source.getProductName()),
                nullToEmpty(source.getBrand()),
                nullToEmpty(source.getMainCategoryEn()),
                nullToEmpty(source.getCategoryTags()),
                nullToEmpty(source.getAllergens()),
                nullToEmpty(source.getIngredientsText()),
                restrictions.isBlank() ? "none" : restrictions);
    }

    private DiscoveryResolution resolveCandidates(
            CatalogProduct source,
            LlmRecommendationCandidatePayload payload) {
        if (payload == null || payload.candidates() == null || payload.candidates().isEmpty()) {
            return new DiscoveryResolution(List.of(), 0, 0, "{\"candidates\":[]}");
        }

        Map<String, CatalogProduct> resolved = new LinkedHashMap<>();
        int rejected = 0;
        for (LlmRecommendationCandidatePayload.Candidate candidate : payload.candidates()) {
            if (candidate == null || candidate.barcode() == null || candidate.barcode().isBlank()) {
                rejected++;
                continue;
            }
            String barcode = candidate.barcode().trim();
            if (barcode.equals(source.getBarcode())) {
                rejected++;
                continue;
            }
            Optional<CatalogProduct> catalogProduct = catalogProductRepository.findById(barcode);
            if (catalogProduct.isPresent()) {
                resolved.putIfAbsent(barcode, catalogProduct.get());
            } else {
                rejected++;
            }
        }

        String auditJson;
        try {
            auditJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            auditJson = "{\"candidates\":[]}";
        }

        return new DiscoveryResolution(
                new ArrayList<>(resolved.values()),
                resolved.size(),
                rejected,
                auditJson);
    }

    private LlmRecommendationCandidatePayload parsePayload(String rawJson) throws JsonProcessingException {
        if (rawJson == null || rawJson.isBlank()) {
            return new LlmRecommendationCandidatePayload(List.of());
        }
        String trimmed = rawJson.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return objectMapper.readValue(trimmed, LlmRecommendationCandidatePayload.class);
    }

    private static String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        return Objects.toString(chatResponse.getResult().getOutput().getText(), "");
    }

    private void recordAudit(
            RecommendationRequest request,
            CatalogProduct source,
            DiscoveryResolution resolution,
            ChatResponse chatResponse,
            long startedAt) {
        if (request.scanId() == null || request.profileId() == null) {
            return;
        }

        Usage usage = metadataUsage(chatResponse);
        recommendationLogService.recordDiscoveryAudit(new RecommendationDiscoveryAudit(
                request.scanId(),
                request.profileId(),
                source.getBarcode(),
                modelId,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L),
                resolution.auditJson(),
                resolution.accepted(),
                resolution.rejected()));
    }

    private static Usage metadataUsage(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        return metadata == null ? null : metadata.getUsage();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record DiscoveryResolution(
            List<CatalogProduct> catalogProducts,
            int accepted,
            int rejected,
            String auditJson) {
    }
}
