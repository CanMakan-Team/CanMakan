package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.verdict.RestrictionRule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the Python UC5 rank service after Spring has filtered SAFE candidates.
 */
@Slf4j
@Component
class PythonTfidfRankClient {

    private final RestClient restClient;
    private final SubstituteDiscoveryProfiles discoveryProfiles;
    private final String rankerUrl;

    @Autowired
    PythonTfidfRankClient(
            SubstituteDiscoveryProfiles discoveryProfiles,
            @Value("${canmakan.recommendation.ml.ranker-url:}") String rankerUrl,
            @Value("${canmakan.recommendation.ml.ranker-connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${canmakan.recommendation.ml.ranker-read-timeout-ms:2000}") long readTimeoutMs) {
        this.discoveryProfiles = discoveryProfiles;
        this.rankerUrl = rankerUrl == null ? "" : rankerUrl.trim();
        if (this.rankerUrl.isEmpty()) {
            this.restClient = null;
        } else {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
            this.restClient = RestClient.builder()
                    .baseUrl(this.rankerUrl)
                    .requestFactory(requestFactory)
                    .build();
        }
    }

    /**
     * Package-private constructor for tests that inject a {@link RestClient} bound to
     * {@link org.springframework.test.web.client.MockRestServiceServer}.
     */
    PythonTfidfRankClient(SubstituteDiscoveryProfiles discoveryProfiles, RestClient restClient) {
        this.discoveryProfiles = discoveryProfiles;
        this.rankerUrl = restClient != null ? "http://localhost" : "";
        this.restClient = restClient;
    }

    boolean isConfigured() {
        return restClient != null;
    }

    List<AlternativeProductRanker.RankedAlternative> rank(
            CatalogProduct source,
            List<CatalogProduct> acceptableCandidates,
            List<RestrictionRule> rules,
            Set<String> priorSafeBarcodes,
            SubstituteDiscoveryProfile substituteProfile) {
        if (!isConfigured() || source == null || acceptableCandidates == null || acceptableCandidates.isEmpty()) {
            throw new PythonTfidfRankClientException("Python ranker is not configured or has no candidates");
        }

        Map<String, CatalogProduct> byBarcode = acceptableCandidates.stream()
                .filter(candidate -> candidate.getBarcode() != null)
                .collect(Collectors.toMap(
                        CatalogProduct::getBarcode,
                        Function.identity(),
                        (left, right) -> left));

        PythonTfidfRankRequest request = new PythonTfidfRankRequest(
                toPayload(source),
                acceptableCandidates.stream().map(PythonTfidfRankClient::toPayload).toList(),
                toProfileHints(rules, priorSafeBarcodes, substituteProfile));

        try {
            PythonTfidfRankResponse response = Objects.requireNonNull(
                    restClient.post()
                            .uri("/rank")
                            .body(request)
                            .retrieve()
                            .body(PythonTfidfRankResponse.class),
                    "Python rank response body was null");

            List<AlternativeProductRanker.RankedAlternative> ranked = new ArrayList<>();
            if (response.ranked() == null) {
                return ranked;
            }
            for (PythonTfidfRankResponse.PythonTfidfRankedProduct item : response.ranked()) {
                CatalogProduct product = byBarcode.get(item.barcode());
                if (product == null) {
                    continue;
                }
                ranked.add(new AlternativeProductRanker.RankedAlternative(
                        product,
                        item.score() == null ? BigDecimal.ZERO : item.score(),
                        item.matchReason() == null ? "ml_similarity" : item.matchReason()));
            }
            return ranked;
        } catch (RestClientException exception) {
            throw new PythonTfidfRankClientException("Python rank service call failed", exception);
        }
    }

    private PythonTfidfRankRequest.PythonTfidfProfileHints toProfileHints(
            List<RestrictionRule> rules,
            Set<String> priorSafeBarcodes,
            SubstituteDiscoveryProfile substituteProfile) {
        boolean preferLowSugar = rules != null
                && rules.stream().anyMatch(rule -> "LOW_SUGAR".equals(rule.code()));
        List<String> secondaryIncludeTags = substituteProfile == null || substituteProfile.secondaryIncludeTags() == null
                ? List.of()
                : substituteProfile.secondaryIncludeTags();
        List<String> deprioritizeTags = substituteProfile == null || substituteProfile.deprioritizeTags() == null
                ? List.of()
                : substituteProfile.deprioritizeTags();
        List<String> includeTags = substituteProfile == null || substituteProfile.includeTags() == null
                ? List.of()
                : substituteProfile.includeTags();
        return new PythonTfidfRankRequest.PythonTfidfProfileHints(
                preferLowSugar,
                discoveryProfiles.isMilkSubstituteDiscovery(substituteProfile),
                discoveryProfiles.isFlourSubstituteDiscovery(substituteProfile),
                discoveryProfiles.isPeanutSpreadSubstituteDiscovery(substituteProfile),
                secondaryIncludeTags,
                deprioritizeTags,
                includeTags,
                priorSafeBarcodes == null ? List.of() : List.copyOf(priorSafeBarcodes));
    }

    private static PythonTfidfRankRequest.PythonTfidfProductPayload toPayload(CatalogProduct product) {
        return new PythonTfidfRankRequest.PythonTfidfProductPayload(
                product.getBarcode(),
                product.getProductName(),
                product.getBrand(),
                product.getMainCategoryEn(),
                product.getCategoryTags(),
                product.getLabelsTags(),
                product.getIngredientsText(),
                product.getQuantity(),
                product.getServingSize(),
                product.getServingQuantity(),
                product.getSugars100g(),
                product.getSodium100g());
    }
}
