package com.canmakan.backend.knowledgebase.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parses Tavily-style allergen summaries into structured external matches.
 *
 * @author Amelia
 */
@DisplayName("UC3: ExternalAllergenMatchParser")
class ExternalAllergenMatchParserTest {

    @Test
    @DisplayName("Parses Ingredient -> ROOT lines")
    void parsesArrowLines() {
        List<Ingredient> matches = ExternalAllergenMatchParser.parse(
                List.of("Casein", "Inulin"),
                """
                Casein -> DAIRY
                Inulin -> NONE
                """
        );

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).ingredientName()).isEqualTo("Casein");
        assertThat(matches.get(0).rootAllergen()).isEqualTo("DAIRY");
        assertThat(matches.get(1).ingredientName()).isEqualTo("Inulin");
        assertThat(matches.get(1).rootAllergen()).isEqualTo("NONE");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("singleMatchCases")
    @DisplayName("Parses a single ingredient match from a summary")
    void parsesSingleMatchFromSummary(
            String description,
            List<String> unresolvedIngredients,
            String summary,
            String expectedIngredientName,
            String expectedRootAllergen) {
        List<Ingredient> matches = ExternalAllergenMatchParser.parse(unresolvedIngredients, summary);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().ingredientName()).isEqualTo(expectedIngredientName);
        assertThat(matches.getFirst().rootAllergen()).isEqualTo(expectedRootAllergen);
    }

    private static Stream<Arguments> singleMatchCases() {
        return Stream.of(
                Arguments.of(
                        "strips a leading dash bullet marker",
                        List.of("Casein"), "- Casein -> DAIRY",
                        "Casein", "DAIRY"),
                Arguments.of(
                        "strips a leading asterisk bullet marker",
                        List.of("Casein"), "* Casein -> DAIRY",
                        "Casein", "DAIRY"),
                Arguments.of(
                        "maps milk alias token to DAIRY",
                        List.of("Whey powder"), "Whey powder -> MILK",
                        "Whey powder", "DAIRY"),
                Arguments.of(
                        "falls back to prose window near ingredient name",
                        List.of("Casein"), "Casein belongs to the dairy family of allergens.",
                        "Casein", "DAIRY"),
                Arguments.of(
                        "matches when the arrow-line label contains the unresolved ingredient",
                        List.of("Casein"), "Casein protein isolate -> DAIRY",
                        "Casein", "DAIRY"),
                Arguments.of(
                        "matches when the unresolved ingredient contains the arrow-line label",
                        List.of("Casein protein isolate"), "Casein -> DAIRY",
                        "Casein protein isolate", "DAIRY"),
                Arguments.of(
                        "skips null and blank entries in the unresolved-ingredients list",
                        Arrays.asList("Casein", null, "   "), "Casein -> DAIRY",
                        "Casein", "DAIRY"),
                Arguments.of(
                        "loose prose fallback skips an ingredient already resolved by an arrow line",
                        List.of("Casein"), "Casein -> DAIRY\nCasein is also linked to the dairy family.",
                        "Casein", "DAIRY")
        );
    }

    @Test
    @DisplayName("Blank or null summary yields no matches")
    void blankOrNullSummaryYieldsNoMatches() {
        assertThat(ExternalAllergenMatchParser.parse(List.of("Casein"), "  ")).isEmpty();
        assertThat(ExternalAllergenMatchParser.parse(List.of("Casein"), null)).isEmpty();
    }

    @Test
    @DisplayName("Null or empty unresolved-ingredients list yields no matches")
    void nullOrEmptyUnresolvedListYieldsNoMatches() {
        assertThat(ExternalAllergenMatchParser.parse(null, "Casein -> DAIRY")).isEmpty();
        assertThat(ExternalAllergenMatchParser.parse(List.of(), "Casein -> DAIRY")).isEmpty();
    }

    @Test
    @DisplayName("Skips an arrow line whose root token is not a known alias")
    void skipsArrowLineWithUnknownRootToken() {
        assertThat(ExternalAllergenMatchParser.parse(
                List.of("Casein"), "Casein -> NOTAKNOWNROOT")).isEmpty();
    }

    @Test
    @DisplayName("Skips an arrow line whose label matches no unresolved ingredient")
    void skipsArrowLineWithNoMatchingUnresolvedIngredient() {
        assertThat(ExternalAllergenMatchParser.parse(
                List.of("Casein"), "Something else -> DAIRY")).isEmpty();
    }

    @Test
    @DisplayName("Loose prose fallback skips an ingredient never mentioned in the summary")
    void looseProseSkipsIngredientNotMentionedInSummary() {
        assertThat(ExternalAllergenMatchParser.parse(
                List.of("Xylitol"), "Casein belongs to the dairy family of allergens.")).isEmpty();
    }

    @Test
    @DisplayName("Loose prose fallback skips an ingredient mentioned with no root word nearby")
    void looseProseSkipsIngredientWithNoNearbyRootWord() {
        assertThat(ExternalAllergenMatchParser.parse(
                List.of("Casein"), "Casein is an interesting compound to study.")).isEmpty();
    }

    @Test
    @DisplayName("canonicalRoot returns null for a null or blank token")
    void canonicalRootReturnsNullForNullOrBlankToken() {
        assertThat(ExternalAllergenMatchParser.canonicalRoot(null)).isNull();
        assertThat(ExternalAllergenMatchParser.canonicalRoot("   ")).isNull();
    }

    @Test
    @DisplayName("canonicalRoot maps aliases")
    void canonicalRootMapsAliases() {
        assertThat(ExternalAllergenMatchParser.canonicalRoot("milk")).isEqualTo("DAIRY");
        assertThat(ExternalAllergenMatchParser.canonicalRoot("TREE NUT")).isEqualTo("TREE_NUT");
        assertThat(ExternalAllergenMatchParser.canonicalRoot("nope")).isNull();
    }
}
