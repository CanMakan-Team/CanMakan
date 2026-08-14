package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.canmakan.backend.product.scan.Scan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class RecommendationService {
	private static final int MAX_RESULTS = 5;
	private static final int MIN_ALTERNATIVES = 5;
    private final RestrictionRuleLoader restrictionRuleLoader;
    private final AlternativeProductQueryService queryService;
    private final SubstituteDiscoveryProfiles discoveryProfiles;
    private final CatalogProductMapper catalogProductMapper;
    private final DietaryRuleEngine ruleEngine;
    private final AlternativeProductRanker ranker;
    private final AlternativeCandidateFilter candidateFilter;
    private final RecommendationLogService logService;
    private final ScanRepository scanRepository;
    private final MlSparseCatalogRecommender mlSparseCatalogRecommender;
    private final MlContentBasedRanker mlContentBasedRanker;
    private final LlmRecommendationDiscoveryService llmRecommendationDiscoveryService;

    @Value("${canmakan.recommendation.ml.enabled:true}")
    private boolean mlRecommendationEnabled;

	private Set<String> loadPriorSafeBarcodes(Long profileId) {
	    return scanRepository.findByProfileIdOrderByScannedAtDesc(profileId).stream()
	        .filter(s -> "SAFE".equals(s.getVerdict()))
	        .map(Scan::getBarcode)
	        .filter(Objects::nonNull)
	        .collect(Collectors.toSet());
	}
	public AlternativeProductResponse recommend(RecommendationRequest request) {
	    // --- validate input ---
	    if (request == null
	            || request.profileId() == null
	            || request.sourceBarcode() == null
	            || request.sourceBarcode().isBlank()) {
	        return AlternativeProductResponse.empty();
	    }

	    // --- step 1: load scanned product from catalog ---
	    CatalogProduct source = queryService.findByBarcode(request.sourceBarcode().trim())
	        .orElse(null);
	    if (source == null || !source.isRecommendationEligible()) {
	        return AlternativeProductResponse.empty(request.sourceBarcode());
	    }

	    // --- step 2: load profile restrictions ---
	    List<RestrictionRule> rules = restrictionRuleLoader.load(request.profileId());
	    boolean preferLowSodiumSauceSubstitutes = AlternativeCandidateFilter.hasLowSodiumPreference(rules)
	            && SubstituteDiscoveryProfiles.isSauceSource(source);

	    // --- step 3: query same-category candidates (exclude source) ---
	    List<CatalogProduct> candidates = preferLowSodiumSauceSubstitutes
	            ? List.of()
	            : queryService.findSameCategoryCandidates(source);

	    // --- step 4: keep only SAFE alternatives ---
	    List<CatalogProduct> acceptableCandidates = filterAcceptable(candidates, rules, null);
	    MatchProvenance provenance = MatchProvenance.SAME_CATEGORY;
	    SubstituteDiscoveryProfile substituteProfile = null;
	    RecommendationDiscoveryTier discoveryTier = RecommendationDiscoveryTier.TIER_A_CATALOG;

	    if (acceptableCandidates.isEmpty()) {
	        Optional<SubstituteDiscoveryProfile> profile =
	                discoveryProfiles.forSourceProduct(source);
	        if (profile.isPresent()) {
	            substituteProfile = profile.get();
	            List<CatalogProduct> tagCandidates = preferLowSodiumSauceSubstitutes
	                    ? queryService.findExpandedSubstituteCandidates(source, substituteProfile)
	                    : queryService.findSubstituteTagCandidates(source, substituteProfile);
	            acceptableCandidates = filterAcceptable(tagCandidates, rules, substituteProfile);
	            provenance = MatchProvenance.SUBSTITUTE_TAG;
	        }
	    }

	    if (acceptableCandidates.size() < MIN_ALTERNATIVES && mlRecommendationEnabled) {
	        if (substituteProfile == null) {
	            substituteProfile = discoveryProfiles.forSourceProduct(source).orElse(null);
	        }
	        Set<String> alreadyFound = acceptableCandidates.stream()
	                .map(CatalogProduct::getBarcode)
	                .collect(Collectors.toSet());
	        List<CatalogProduct> mlCandidates =
	                mlSparseCatalogRecommender.discoverCandidates(source, substituteProfile, alreadyFound);
	        List<CatalogProduct> extraAcceptable = filterAcceptable(mlCandidates, rules, substituteProfile);
	        if (!extraAcceptable.isEmpty()) {
	            acceptableCandidates = mergeByBarcode(acceptableCandidates, extraAcceptable, source.getBarcode());
	            provenance = MatchProvenance.ML_SIMILARITY;
	            discoveryTier = RecommendationDiscoveryTier.TIER_C_ML_SPARSE;
	        }
	    }

	    if (acceptableCandidates.isEmpty() && llmRecommendationDiscoveryService.isEnabled()) {
	        List<CatalogProduct> llmCandidates =
	                llmRecommendationDiscoveryService.discoverCandidates(request, source, rules);
	        acceptableCandidates = filterAcceptable(llmCandidates, rules, substituteProfile);
	        if (!acceptableCandidates.isEmpty()) {
	            provenance = MatchProvenance.LLM_DISCOVERY;
	            discoveryTier = RecommendationDiscoveryTier.TIER_B_LLM_DISCOVERY;
	        }
	    }

	    if (acceptableCandidates.isEmpty()) {
	        return AlternativeProductResponse.empty(source.getBarcode());
	    }

	    acceptableCandidates = dedupeCandidates(acceptableCandidates, source.getBarcode());

	    // --- step 5: rank ---
	    Set<String> priorSafe = loadPriorSafeBarcodes(request.profileId());
	    List<AlternativeProductRanker.RankedAlternative> ranked = rankCandidates(
	            source,
	            rules,
	            acceptableCandidates,
	            priorSafe,
	            provenance,
	            substituteProfile);
	    if (shouldUseMlRanking(source, provenance)) {
	        discoveryTier = RecommendationDiscoveryTier.TIER_C_ML_SPARSE;
	    }

	    // --- step 6: take top N ---
	    List<AlternativeProductRanker.RankedAlternative> top = ranked.stream()
	        .limit(MAX_RESULTS)
	        .toList();

	    // --- step 7: log (best-effort) ---
	    logService.recordAlternatives(toLogEntries(request, source, top, discoveryTier));

	    // --- step 8: return API response ---
	    return toResponse(source.getBarcode(), top);
	}

	private List<AlternativeProductRanker.RankedAlternative> rankCandidates(
	        CatalogProduct source,
	        List<RestrictionRule> rules,
	        List<CatalogProduct> acceptableCandidates,
	        Set<String> priorSafe,
	        MatchProvenance provenance,
	        SubstituteDiscoveryProfile substituteProfile) {

	    if (shouldUseMlRanking(source, provenance)) {
	        return mlContentBasedRanker.rank(source, acceptableCandidates, rules, priorSafe);
	    }
	    if (provenance == MatchProvenance.LLM_DISCOVERY) {
	        return ranker.rankSameCategory(acceptableCandidates, priorSafe);
	    }
	    if (provenance == MatchProvenance.SUBSTITUTE_TAG) {
	        return ranker.rankSubstituteTags(acceptableCandidates, priorSafe, substituteProfile);
	    }
	    return ranker.rankSameCategory(acceptableCandidates, priorSafe);
	}

	private boolean shouldUseMlRanking(CatalogProduct source, MatchProvenance provenance) {
	    if (!mlRecommendationEnabled || provenance == MatchProvenance.LLM_DISCOVERY) {
	        return false;
	    }
	    return provenance == MatchProvenance.ML_SIMILARITY
	            || mlSparseCatalogRecommender.isSparseSource(source);
	}

	private List<CatalogProduct> filterAcceptable(
	        List<CatalogProduct> candidates,
	        List<RestrictionRule> rules,
	        SubstituteDiscoveryProfile substituteProfile) {
	    boolean flourSubstituteDiscovery = discoveryProfiles.isFlourSubstituteDiscovery(substituteProfile);
	    boolean peanutSpreadDiscovery = discoveryProfiles.isPeanutSpreadSubstituteDiscovery(substituteProfile);
	    boolean iceCreamSubstituteDiscovery = discoveryProfiles.isIceCreamSubstituteDiscovery(substituteProfile);
	    boolean breadSubstituteDiscovery = discoveryProfiles.isBreadSubstituteDiscovery(substituteProfile);
	    boolean breakfastCerealSubstituteDiscovery =
	            discoveryProfiles.isBreakfastCerealSubstituteDiscovery(substituteProfile);
	    boolean lowSodiumSauceSubstituteDiscovery =
	            discoveryProfiles.isLowSodiumSauceSubstituteDiscovery(substituteProfile);
	    return candidates.stream()
	            .filter(candidate -> !flourSubstituteDiscovery
	                    || AlternativeCandidateFilter.isFlourSubstitute(candidate))
	            .filter(candidate -> !peanutSpreadDiscovery
	                    || AlternativeCandidateFilter.isPeanutFreeSpreadSubstitute(candidate))
	            .filter(candidate -> !iceCreamSubstituteDiscovery
	                    || AlternativeCandidateFilter.isIceCreamSubstitute(candidate))
	            .filter(candidate -> !breadSubstituteDiscovery
	                    || AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(candidate))
	            .filter(candidate -> !breakfastCerealSubstituteDiscovery
	                    || AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(candidate))
	            .filter(candidate -> !lowSodiumSauceSubstituteDiscovery
	                    || AlternativeCandidateFilter.isLowSodiumSauceSubstitute(candidate))
	            .filter(candidate -> isAcceptableAlternative(candidate, rules))
	            .toList();
	}

	private static List<CatalogProduct> dedupeCandidates(
	        List<CatalogProduct> candidates,
	        String sourceBarcode) {
	    return mergeByBarcode(List.of(), candidates, sourceBarcode);
	}

	private static List<CatalogProduct> mergeByBarcode(
	        List<CatalogProduct> existing,
	        List<CatalogProduct> extra,
	        String sourceBarcode) {
	    Map<String, CatalogProduct> merged = new LinkedHashMap<>();
	    for (CatalogProduct candidate : existing) {
	        addCandidateByBarcode(merged, candidate, sourceBarcode);
	    }
	    for (CatalogProduct candidate : extra) {
	        addCandidateByBarcode(merged, candidate, sourceBarcode);
	    }
	    return new ArrayList<>(merged.values());
	}

	private static void addCandidateByBarcode(
	        Map<String, CatalogProduct> merged,
	        CatalogProduct candidate,
	        String sourceBarcode) {
	    if (candidate.getBarcode() == null || candidate.getBarcode().equals(sourceBarcode)) {
	        return;
	    }
	    merged.putIfAbsent(candidate.getBarcode(), candidate);
	}

	private boolean isAcceptableAlternative(CatalogProduct candidate, List<RestrictionRule> rules) {
	    ProductData productData = catalogProductMapper.toProductData(candidate);
	    SafetyVerdict verdict = ruleEngine.assessForRecommendation(rules, productData);
	    return candidateFilter.isAcceptableAlternative(rules, verdict, candidate);
	}
	private List<RecommendationLogEntry> toLogEntries(
	        RecommendationRequest request,
	        CatalogProduct source,
	        List<AlternativeProductRanker.RankedAlternative> ranked,
	        RecommendationDiscoveryTier discoveryTier) {

	    return ranked.stream()
	        .map(r -> new RecommendationLogEntry(
	            request.profileId(),
	            request.scanId(),
	            source.getBarcode(),
	            r.product().getBarcode(),
	            r.product().getProductName(),
	            r.product().getBrand(),
	            discoveryTier,
	            r.score(),
	            r.matchReason(),
	            RecommendationDataQuality.VERIFIED,
	            true
	        ))
	        .toList();
	}
	
	private AlternativeProductResponse toResponse(
	        String sourceBarcode,
	        List<AlternativeProductRanker.RankedAlternative> ranked) {

	    List<AlternativeProductDto> alternatives = ranked.stream()
	        .map(r -> new AlternativeProductDto(
	            r.product().getBarcode(),
	            r.product().getProductName(),
	            r.product().getBrand(),
	            r.matchReason(),
	            r.score()
	        ))
	        .toList();

	    return new AlternativeProductResponse(sourceBarcode, alternatives);
	}
}
