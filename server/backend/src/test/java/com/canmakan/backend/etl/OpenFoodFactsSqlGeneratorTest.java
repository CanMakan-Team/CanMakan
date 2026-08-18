package com.canmakan.backend.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link OpenFoodFactsSqlGenerator#run} end to end against small, hand-built
 * Open Food Facts TSV fixtures, since its column parsing/formatting helpers are private.
 *
 * @author Amelia
 */
@DisplayName("OpenFoodFactsSqlGenerator")
class OpenFoodFactsSqlGeneratorTest {

    // Header names in the same order the exporter's Column enum expects them.
    private static final String HEADER_LINE = String.join("\t",
        "code", "product_name", "generic_name", "brands", "quantity", "serving_size",
        "serving_quantity", "categories", "categories_tags", "main_category", "main_category_en",
        "food_groups", "food_groups_tags", "ingredients_text", "ingredients_analysis_tags",
        "allergens", "allergens_en", "traces_tags", "traces_en", "labels_tags", "labels_en",
        "countries_tags", "image_url", "nutriscore_grade", "no_nutrition_data", "completeness",
        "energy-kcal_100g", "energy_100g", "sugars_100g", "added-sugars_100g", "proteins_100g",
        "carbohydrates_100g", "fat_100g", "saturated-fat_100g", "trans-fat_100g",
        "cholesterol_100g", "fiber_100g", "sodium_100g", "salt_100g", "added-salt_100g",
        "alcohol_100g");

    @Test
    @DisplayName("writes an INSERT tuple for a valid Singapore product and skips invalid rows")
    void writesInsertForValidSingaporeProductAndSkipsInvalidRows() throws Exception {
        String longGenericName = "x".repeat(300);
        String validRow = row(
            "1234567890123", "Kaya's Toast \\ Special", longGenericName, "Ya Kun", "1 pack",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "en:singapore", "", "b", "", "80",
            "250.5", "", "", "", "", "",
            "", "", "", "", "", "", "", "", "");
        // Not a Singapore product at all (no "singapore" text anywhere in the line) → skipped fast.
        String nonSingaporeRow = row(
            "1111111111111", "Curry Puff", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "en:malaysia", "", "", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        // Mentions "singapore" (lowercase, so the cheap substring gate passes) but countries_tags
        // doesn't list Singapore → rejected by isSingaporeProduct itself.
        String mentionsSingaporeButWrongCountry = row(
            "2222222222222", "singapore chili sauce", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "", "en:indonesia", "", "", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        // Valid country, but blank barcode → skipped.
        String blankBarcodeRow = row(
            "", "No Barcode Snack", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "en:singapore", "", "", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        // Valid country and barcode, but blank product name → skipped.
        String blankNameRow = row(
            "6666666666666", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "en:singapore", "", "", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        // Valid country, invalid decimal in completeness → NULL, not a skip.
        String badDecimalRow = row(
            "3333333333333", "Mystery Snack", "", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "en:singapore", "", "", "", "not-a-number",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        Path input = tempInputGz(
            HEADER_LINE, validRow, nonSingaporeRow, mentionsSingaporeButWrongCountry,
            blankBarcodeRow, blankNameRow, badDecimalRow);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        String sql = Files.readString(output);
        assertThat(sql)
            .contains("INSERT INTO products (")
            .contains("'1234567890123'")
            // Escaped: backslash doubled, and the truncated 255-char generic name is present.
            .contains("Kaya''s Toast \\\\ Special")
            .contains("x".repeat(255))
            .doesNotContain("x".repeat(256))
            .contains("'3333333333333'")
            // The invalid decimal became NULL rather than being rejected outright.
            .doesNotContain("not-a-number")
            // Skipped rows never appear.
            .doesNotContain("1111111111111")
            .doesNotContain("2222222222222")
            .doesNotContain("No Barcode Snack")
            .doesNotContain("6666666666666")
            .contains("ON DUPLICATE KEY UPDATE");
    }

    @Test
    @DisplayName("falls back to nutrition_grade_fr when nutriscore_grade is absent from the header")
    void fallsBackToLegacyNutritionGradeColumn() throws Exception {
        String header = HEADER_LINE.replace("nutriscore_grade", "nutrition_grade_fr");
        String validRow = row(
            "4444444444444", "Legacy Grade Snack", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "en:singapore", "", "c", "", "",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        Path input = tempInputGz(header, validRow);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        assertThat(Files.readString(output)).contains("'4444444444444'");
    }

    @Test
    @DisplayName("treats a row with fewer columns than the countries_tags index as not-Singapore")
    void treatsShortRowAsNotSingapore() throws Exception {
        // "singapore" appears in the product name so the cheap substring gate passes, but the
        // row is truncated before the countries_tags column, so isSingaporeProduct must reject it.
        String shortRow = "5555555555555\tSingapore Snack (short row)";

        Path input = tempInputGz(HEADER_LINE, shortRow);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        assertThat(Files.readString(output)).doesNotContain("5555555555555");
    }

    @Test
    @DisplayName("flushes a full batch mid-stream and the remainder at the end")
    void flushesBatchesAtBatchSizeAndAtEndOfFile() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            String barcode = String.format("9%011d", i);
            rows.append(row(
                barcode, "Batch Snack " + i, "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "en:singapore", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
            rows.append('\n');
        }
        Path input = tempInputGzRaw(HEADER_LINE + "\n" + rows);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        String sql = Files.readString(output);
        int batchCount = sql.split("INSERT INTO products \\(", -1).length - 1;
        assertThat(batchCount).isEqualTo(2);
        assertThat(sql)
            .contains("'900000000000'")
            .contains("'900000000500'");
    }

    @Test
    @DisplayName("logs and returns without writing when either configured path is blank")
    void returnsEarlyWhenPathsAreBlank() throws Exception {
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator("", output.toString()).run();

        assertThat(Files.exists(output)).isFalse();
    }

    @Test
    @DisplayName("logs and returns without writing when only the output path is blank")
    void returnsEarlyWhenOnlyOutputPathIsBlank() throws Exception {
        Path input = tempInputGz(HEADER_LINE);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> new OpenFoodFactsSqlGenerator(input.toString(), "").run());
    }

    @Test
    @DisplayName("treats a row as not-Singapore when the header has no countries_tags column at all")
    void treatsRowAsNotSingaporeWhenCountriesTagsColumnIsMissing() throws Exception {
        String headerWithoutCountriesTags = HEADER_LINE.replace("countries_tags\t", "");
        // "singapore" appears in the product name so the cheap substring gate still passes.
        String row = "7777777777777\tsingapore snack, no countries column";

        Path input = tempInputGz(headerWithoutCountriesTags, row);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        assertThat(Files.readString(output)).doesNotContain("7777777777777");
    }

    @Test
    @DisplayName("fills missing trailing columns with NULL when a valid row is shorter than the header")
    void fillsMissingTrailingColumnsWithNullForShortValidRow() throws Exception {
        // Only code(0)..countries_tags(21) are present; every later column is entirely absent
        // from this row, so getValue must fall back to "" (-> NULL) for all of them.
        String shortRow = String.join("\t",
            "8888888888888", "Short Row Snack", "", "", "", "", "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "en:singapore");

        Path input = tempInputGz(HEADER_LINE, shortRow);
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        String sql = Files.readString(output);
        assertThat(sql)
            .contains("'8888888888888'")
            .contains("'Short Row Snack'");
    }

    @Test
    @DisplayName("writes nothing when the input file has no header line")
    void writesNothingForEmptyInputFile() throws Exception {
        Path input = tempInputGzRaw("");
        Path output = tempDir.resolve("out.sql");

        new OpenFoodFactsSqlGenerator(input.toString(), output.toString()).run();

        assertThat(Files.readString(output)).isEmpty();
    }

    @Test
    @DisplayName("logs and completes without throwing when the input path does not exist")
    void completesWithoutThrowingWhenInputMissing() {
        Path missing = tempDir.resolve("does-not-exist.csv.gz");
        Path output = tempDir.resolve("out.sql");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> new OpenFoodFactsSqlGenerator(missing.toString(), output.toString()).run());
    }

    @TempDir
    private Path tempDir;

    private Path tempInputGz(String... lines) throws IOException {
        return tempInputGzRaw(String.join("\n", lines));
    }

    private Path tempInputGzRaw(String content) throws IOException {
        Path gzPath = tempDir.resolve("input-" + System.nanoTime() + ".csv.gz");
        try (Writer writer = new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(gzPath)), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return gzPath;
    }

    /** Builds one tab-separated data row matching {@link #HEADER_LINE}'s column order. */
    private static String row(String... values) {
        return String.join("\t", values);
    }
}
