package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

@DisplayName("UC5: PythonTfidfRankClient")
class PythonTfidfRankClientTest {

    private static final String RANK_URL = "http://localhost/rank";

    private final SubstituteDiscoveryProfiles discoveryProfiles = new SubstituteDiscoveryProfiles();

    @Test
    void isNotConfiguredWhenRankerUrlEmpty() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                discoveryProfiles, "", 500, 2000);
        assertFalse(client.isConfigured());
    }

    @Test
    void isConfiguredWhenRankerUrlPresent() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                discoveryProfiles, "http://127.0.0.1:8091", 500, 2000);
        assertTrue(client.isConfigured());
    }

    @Test
    void treatsNullRankerUrlAsNotConfigured() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                discoveryProfiles, null, 500, 2000);
        assertFalse(client.isConfigured());
    }

    @Test
    void rankMapsSuccessfulResponse() {
        PythonTfidfRankClient client = clientWithMockServer(withSuccess("""
                {
                  "ranked": [
                    {"barcode": "8850025000521", "score": 0.85, "match_reason": "ml_unsweetened_substitute"}
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        CatalogProduct source = milkSource("8888200602857");
        CatalogProduct candidate = plantMilk("8850025000521", "Soya Milk Unsweetened");

        List<AlternativeProductRanker.RankedAlternative> ranked = client.rank(
                source,
                List.of(candidate),
                lowSugarRules(),
                Set.of(),
                milkSubstituteProfile());

        assertEquals(1, ranked.size());
        assertEquals("8850025000521", ranked.getFirst().product().getBarcode());
        assertEquals(new BigDecimal("0.85"), ranked.getFirst().score());
        assertEquals("ml_unsweetened_substitute", ranked.getFirst().matchReason());
    }

    @Test
    void rankSkipsUnknownBarcodesAndDefaultsMissingScoreFields() {
        PythonTfidfRankClient client = clientWithMockServer(withSuccess("""
                {
                  "ranked": [
                    {"barcode": "unknown-barcode", "score": 0.99, "match_reason": "ml_similarity"},
                    {"barcode": "8850025000521", "score": null, "match_reason": null}
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        CatalogProduct candidate = plantMilk("8850025000521", "Soya Milk Unsweetened");
        List<AlternativeProductRanker.RankedAlternative> ranked = client.rank(
                milkSource("8888200602857"),
                List.of(candidate),
                List.of(),
                Set.of(),
                milkSubstituteProfile());

        assertEquals(1, ranked.size());
        assertEquals(BigDecimal.ZERO, ranked.getFirst().score());
        assertEquals("ml_similarity", ranked.getFirst().matchReason());
    }

    @Test
    void rankReturnsEmptyWhenResponseRankedListIsNull() {
        PythonTfidfRankClient client = clientWithMockServer(withSuccess(
                "{\"ranked\": null}", MediaType.APPLICATION_JSON));

        List<AlternativeProductRanker.RankedAlternative> ranked = client.rank(
                milkSource("8888200602857"),
                List.of(plantMilk("8850025000521", "Soya Milk Unsweetened")),
                List.of(),
                Set.of(),
                milkSubstituteProfile());

        assertTrue(ranked.isEmpty());
    }

    @Test
    void rankThrowsWhenNotConfigured() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(discoveryProfiles, "", 500, 2000);

        PythonTfidfRankClientException exception = assertThrows(
                PythonTfidfRankClientException.class,
                () -> client.rank(
                        milkSource("8888200602857"),
                        List.of(plantMilk("8850025000521", "Soya Milk Unsweetened")),
                        List.of(),
                        Set.of(),
                        milkSubstituteProfile()));

        assertEquals("Python ranker is not configured or has no candidates", exception.getMessage());
    }

    @Test
    void rankThrowsWhenSourceOrCandidatesMissing() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                discoveryProfiles, "http://127.0.0.1:8091", 500, 2000);

        assertThrows(
                PythonTfidfRankClientException.class,
                () -> client.rank(null, List.of(), List.of(), Set.of(), null));
        assertThrows(
                PythonTfidfRankClientException.class,
                () -> client.rank(
                        milkSource("8888200602857"),
                        List.of(),
                        List.of(),
                        Set.of(),
                        null));
    }

    @Test
    void rankWrapsHttpFailures() {
        PythonTfidfRankClient client = clientWithMockServer(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        PythonTfidfRankClientException exception = assertThrows(
                PythonTfidfRankClientException.class,
                () -> client.rank(
                        milkSource("8888200602857"),
                        List.of(plantMilk("8850025000521", "Soya Milk Unsweetened")),
                        List.of(),
                        Set.of(),
                        milkSubstituteProfile()));

        assertEquals("Python rank service call failed", exception.getMessage());
        assertTrue(exception.getCause() != null);
    }

    @Test
    void exceptionSupportsMessageOnlyConstructor() {
        PythonTfidfRankClientException exception = new PythonTfidfRankClientException("ranker down");
        assertEquals("ranker down", exception.getMessage());
    }

    @Test
    void exceptionSupportsMessageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("timeout");
        PythonTfidfRankClientException exception =
                new PythonTfidfRankClientException("Python rank service call failed", cause);
        assertEquals("Python rank service call failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    private PythonTfidfRankClient clientWithMockServer(ResponseCreator response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(RANK_URL))
                .andExpect(method(POST))
                .andRespond(response);
        return new PythonTfidfRankClient(discoveryProfiles, builder.build());
    }

    private static List<RestrictionRule> lowSugarRules() {
        return List.of(
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE));
    }

    private static SubstituteDiscoveryProfile milkSubstituteProfile() {
        return new SubstituteDiscoveryProfile(
                List.of("en:milk-substitutes", "en:soy-based-drinks"),
                List.of("en:soy-based-drinks", "en:unsweetened-plain-soy-based-drinks"),
                List.of("en:plant-based-creams-for-cooking"),
                List.of("en:vegan"),
                List.of());
    }

    private static CatalogProduct milkSource(String barcode) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Farmhouse Fresh Milk");
        product.setBrand("Farmhouse");
        product.setMainCategoryEn("Fresh milks");
        product.setCategoryTags("en:fresh-milks,en:milks");
        product.setLabelsTags("en:milk-and-yogurt");
        product.setIngredientsText("Fresh milks");
        product.setQuantity("1 l");
        product.setServingSize("250 ml");
        product.setServingQuantity(new BigDecimal("250"));
        product.setSugars100g(new BigDecimal("4.8"));
        product.setSodium100g(new BigDecimal("0.05"));
        return product;
    }

    private static CatalogProduct plantMilk(String barcode, String name) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setBrand("Home Soy");
        product.setMainCategoryEn("Unsweetened plain soy-based drinks");
        product.setCategoryTags(
                "en:milk-substitutes,en:soy-based-drinks,en:unsweetened-plain-soy-based-drinks");
        product.setLabelsTags("en:plant-based-milk-substitutes");
        product.setIngredientsText("Soy milk 99.6%, Calcium Carbonate");
        product.setQuantity("1 Litre");
        product.setServingSize("200 ml");
        product.setServingQuantity(new BigDecimal("200"));
        product.setSugars100g(new BigDecimal("1.2"));
        product.setSodium100g(new BigDecimal("0.04"));
        return product;
    }
}
