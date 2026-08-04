package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.integration.ProductLookupException;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.FindingType;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Coordinates the tiered "scan product barcode -> view scan verdict" flow:
 *
 * <ol>
 *   <li>load the profile's active restriction rules</li>
 *   <li>retrieve and adapt the product data</li>
 *   <li>run the deterministic dietary rule engine</li>
 *   <li>use the LLM only for unresolved or ambiguous evidence</li>
 *   <li>persist the scan and execution log</li>
 * </ol>
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class AssessmentOrchestrator {

    private final ProductDataAdapter productDataAdapter;
    private final BarcodeValidationClient barcodeValidationClient;
    private final RestrictionRuleLoader ruleLoader;
    private final DietaryRuleEngine ruleEngine;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ScanService scanService;
    private final AiExecutionLogService aiExecutionLogService;

    public AssessmentOrchestrator(
            ProductDataAdapter productDataAdapter,
            BarcodeValidationClient barcodeValidationClient,
            RestrictionRuleLoader ruleLoader,
            DietaryRuleEngine ruleEngine,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            ScanService scanService,
            AiExecutionLogService aiExecutionLogService) {
        this.productDataAdapter = productDataAdapter;
        this.barcodeValidationClient = barcodeValidationClient;
        this.ruleLoader = ruleLoader;
        this.ruleEngine = ruleEngine;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.scanService = scanService;
        this.aiExecutionLogService = aiExecutionLogService;
    }

    /**
     * Assesses one product for one profile and persists the result.
     *
     * @param userId verified authenticated user ID
     * @param request barcode and profile ID
     * @return the verdict, execution tier, and saved scan ID
     */
    public AssessmentResponse assess(Long userId, AssessmentRequest request) {
        Objects.requireNonNull(userId, "verified userId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.profileId(), "profileId");

        if (request.barcode() == null || request.barcode().isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }

        String barcode = request.barcode().trim();
        List<RestrictionRule> rules = ruleLoader.load(request.profileId());

        ProductLookupResult lookupResult = lookupProduct(barcode);
        ProductData product = productDataAdapter.toProductData(lookupResult);

        long rulesStartedAt = System.nanoTime();
        SafetyVerdict verdict = ruleEngine.assess(rules, product);
        long rulesLatencyMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - rulesStartedAt
        );

        ExecutionTier tier = ExecutionTier.TIER_1_RULES;
        LlmAssessmentResult llmResult = null;

        if (shouldEscalate(verdict, product)) {
            String compiledPrompt = promptBuilder.build(
                    product,
                    rules,
                    verdict.findings(),
                    List.of(),
                    UUID.randomUUID().toString()
            );

            llmResult = llmClient.assess(compiledPrompt);
            tier = ExecutionTier.TIER_3_LLM;

            if (llmResult.successful()) {
                verdict = aggregateModelEvidence(rules, verdict, llmResult);
            }
        }

        Scan scan = scanService.record(
                userId,
                request.profileId(),
                barcode,
                verdict
        );

        if (llmResult == null) {
            aiExecutionLogService.recordRulesOnly(
                    scan.getId(),
                    rulesLatencyMs
            );
        } else {
            aiExecutionLogService.record(
                    scan.getId(),
                    tier,
                    llmResult
            );
        }

        return new AssessmentResponse(
                verdict.toScansVerdict(),
                verdict.explanation(),
                verdict.findings(),
                tier,
                scan.getId()
        );
    }

    private boolean shouldEscalate(
            SafetyVerdict verdict,
            ProductData product) {
        if (verdict.level() != SafetyVerdict.Level.WARNING
                || product.ingredients() == null
                || product.ingredients().isEmpty()) {
            return false;
        }

        boolean hasAmbiguousIngredient = product.ingredients().stream()
                .filter(Objects::nonNull)
                .anyMatch(ingredient ->
                        ingredient.chemicalAlias()
                                || ingredient.rootAllergen() == null
                                || ingredient.rootAllergen().isBlank()
                );

        boolean hasResolvableUncertainty = verdict.findings().stream()
                .anyMatch(finding ->
                        finding.type() == FindingType.UNRESOLVED_INGREDIENT
                                || finding.type() == FindingType.INCOMPLETE_DATA
                );

        return hasAmbiguousIngredient && hasResolvableUncertainty;
    }

    private ProductLookupResult lookupProduct(String barcode) {
        try {
            return barcodeValidationClient.fetchProduct(barcode)
                    .orElseGet(() -> incompleteLookup(barcode));
        } catch (ProductLookupException ignored) {
            return incompleteLookup(barcode);
        }
    }

    private ProductLookupResult incompleteLookup(String barcode) {
        return new ProductLookupResult(
                barcode,
                List.of(),
                null,
                null,
                null,
                false
        );
    }

    private SafetyVerdict aggregateModelEvidence(
            List<RestrictionRule> rules,
            SafetyVerdict deterministicVerdict,
            LlmAssessmentResult llmResult) {
        Set<String> applicableCodes = new HashSet<>();

        if (rules != null) {
            rules.stream()
                    .filter(Objects::nonNull)
                    .map(RestrictionRule::code)
                    .filter(Objects::nonNull)
                    .forEach(applicableCodes::add);
        }

        List<Finding> combined =
                new ArrayList<>(deterministicVerdict.findings());

        llmResult.proposedFindings().stream()
                .filter(finding ->
                        finding.restrictionCode() == null
                                || applicableCodes.contains(
                                        finding.restrictionCode()
                                )
                )
                .forEach(combined::add);

        return ruleEngine.aggregate(rules, combined);
    }
}