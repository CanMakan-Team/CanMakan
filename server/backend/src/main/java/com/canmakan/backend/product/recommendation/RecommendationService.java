package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.canmakan.backend.product.scan.Scan;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class RecommendationService {
	private static final int MAX_RESULTS = 5;
    private final RestrictionRuleLoader restrictionRuleLoader;
    private final AlternativeProductQueryService queryService;
    private final CatalogProductMapper catalogProductMapper;
    private final DietaryRuleEngine ruleEngine;
    private final AlternativeProductRanker ranker;
    private final RecommendationLogService logService;
    private final ScanRepository scanRepository;
    
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

	    // --- step 3: query same-category candidates (exclude source) ---
	    List<CatalogProduct> candidates = queryService.findCandidates(source);
	    if (candidates.isEmpty()) {
	        return AlternativeProductResponse.empty(source.getBarcode());
	    }

	    // --- step 4: keep only SAFE alternatives ---
	    List<CatalogProduct> safeCandidates = candidates.stream()
	        .filter(candidate -> isSafeForProfile(candidate, rules))
	        .toList();
	    if (safeCandidates.isEmpty()) {
	        return AlternativeProductResponse.empty(source.getBarcode());
	    }

	    // --- step 5: rank ---
	    Set<String> priorSafe = loadPriorSafeBarcodes(request.profileId());
	    List<AlternativeProductRanker.RankedAlternative> ranked =
	        ranker.rank(safeCandidates, priorSafe);

	    // --- step 6: take top N ---
	    List<AlternativeProductRanker.RankedAlternative> top = ranked.stream()
	        .limit(MAX_RESULTS)
	        .toList();

	    // --- step 7: log (best-effort) ---
	    logService.recordAlternatives(toLogEntries(request, source, top));

	    // --- step 8: return API response ---
	    return toResponse(source.getBarcode(), top);
	}
	private boolean isSafeForProfile(CatalogProduct candidate, List<RestrictionRule> rules) {
	    ProductData productData = catalogProductMapper.toProductData(candidate);
	    SafetyVerdict verdict = ruleEngine.assess(rules, productData);
	    return verdict.level() == SafetyVerdict.Level.SAFE;
	}
	private List<RecommendationLogEntry> toLogEntries(
	        RecommendationRequest request,
	        CatalogProduct source,
	        List<AlternativeProductRanker.RankedAlternative> ranked) {

	    return ranked.stream()
	        .map(r -> new RecommendationLogEntry(
	            request.profileId(),
	            request.scanId(),
	            source.getBarcode(),
	            r.product().getBarcode(),
	            r.product().getProductName(),
	            r.product().getBrand(),
	            RecommendationDiscoveryTier.TIER_A_CATALOG,
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
