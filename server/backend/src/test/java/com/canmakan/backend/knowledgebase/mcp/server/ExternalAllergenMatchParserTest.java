package com.canmakan.backend.knowledgebase.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("Maps milk alias token to DAIRY")
    void mapsMilkAliasToDairy() {
        List<Ingredient> matches = ExternalAllergenMatchParser.parse(
                List.of("Whey powder"),
                "Whey powder -> MILK"
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().rootAllergen()).isEqualTo("DAIRY");
    }

    @Test
    @DisplayName("Falls back to prose window near ingredient name")
    void fallsBackToProseNearIngredient() {
        List<Ingredient> matches = ExternalAllergenMatchParser.parse(
                List.of("Casein"),
                "Casein belongs to the dairy family of allergens."
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().rootAllergen()).isEqualTo("DAIRY");
    }

    @Test
    @DisplayName("Blank summary yields no matches")
    void blankSummaryYieldsNoMatches() {
        assertThat(ExternalAllergenMatchParser.parse(List.of("Casein"), "  ")).isEmpty();
    }

    @Test
    @DisplayName("canonicalRoot maps aliases")
    void canonicalRootMapsAliases() {
        assertThat(ExternalAllergenMatchParser.canonicalRoot("milk")).isEqualTo("DAIRY");
        assertThat(ExternalAllergenMatchParser.canonicalRoot("TREE NUT")).isEqualTo("TREE_NUT");
        assertThat(ExternalAllergenMatchParser.canonicalRoot("nope")).isNull();
    }
}
