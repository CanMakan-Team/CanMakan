package com.canmakan.backend.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

// NOTE: run only to extract data (csv zipped file saved on local folder) from Open food fact
// database to output INSERT statements for products.sql. Enable via the 'generate-sql' profile and
// set canmakan.etl.open-food-facts.input-gz-path / output-sql-path (see application.properties).
@Component
@Profile("generate-sql")
public class OpenFoodFactsSqlGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsSqlGenerator.class);

    private static final int BATCH_SIZE = 500; // Batch 500 rows per INSERT statement

    private static final String INSERT_HEADER = "INSERT INTO products (" +
        "barcode, product_name, generic_name, brand, quantity, serving_size, serving_quantity, " +
        "categories, category_tags, main_category, main_category_en, food_groups, food_groups_tags, " +
        "ingredients_text, ingredients_analysis_tags, allergens, allergens_en, traces_tags, traces_en, " +
        "labels_tags, labels_en, countries_tags, image_url, " +
        "nutrition_grade, no_nutrition_data, completeness, " +
        "energy_kcal_100g, energy_kj_100g, sugars_100g, added_sugars_100g, proteins_100g, carbohydrates_100g, " +
        "fat_100g, saturated_fat_100g, trans_fat_100g, cholesterol_100g, fiber_100g, sodium_100g, salt_100g, added_salt_100g, alcohol_100g) VALUES\n";

    private static final String ON_DUPLICATE_FOOTER =
        "\nON DUPLICATE KEY UPDATE product_name=VALUES(product_name), ingredients_text=VALUES(ingredients_text);\n\n";

    /** Open Food Facts CSV columns this exporter reads, mapped to their header name. */
    private enum Column {
        CODE("code"),
        PRODUCT_NAME("product_name"),
        GENERIC_NAME("generic_name"),
        BRAND("brands"),
        QUANTITY("quantity"),
        SERVING_SIZE("serving_size"),
        SERVING_QUANTITY("serving_quantity"),
        CATEGORIES("categories"),
        CATEGORY_TAGS("categories_tags"),
        MAIN_CATEGORY("main_category"),
        MAIN_CATEGORY_EN("main_category_en"),
        FOOD_GROUPS("food_groups"),
        FOOD_GROUPS_TAGS("food_groups_tags"),
        INGREDIENTS_TEXT("ingredients_text"),
        INGREDIENTS_ANALYSIS_TAGS("ingredients_analysis_tags"),
        ALLERGENS("allergens"),
        ALLERGENS_EN("allergens_en"),
        TRACES_TAGS("traces_tags"),
        TRACES_EN("traces_en"),
        LABELS_TAGS("labels_tags"),
        LABELS_EN("labels_en"),
        COUNTRIES_TAGS("countries_tags"),
        IMAGE_URL("image_url"),
        NUTRITION_GRADE("nutriscore_grade"),
        NO_NUTRITION_DATA("no_nutrition_data"),
        COMPLETENESS("completeness"),
        ENERGY_KCAL("energy-kcal_100g"),
        ENERGY_KJ("energy_100g"),
        SUGARS("sugars_100g"),
        ADDED_SUGARS("added-sugars_100g"),
        PROTEINS("proteins_100g"),
        CARBOHYDRATES("carbohydrates_100g"),
        FAT("fat_100g"),
        SATURATED_FAT("saturated-fat_100g"),
        TRANS_FAT("trans-fat_100g"),
        CHOLESTEROL("cholesterol_100g"),
        FIBER("fiber_100g"),
        SODIUM("sodium_100g"),
        SALT("salt_100g"),
        ADDED_SALT("added-salt_100g"),
        ALCOHOL("alcohol_100g");

        private final String headerName;

        Column(String headerName) {
            this.headerName = headerName;
        }
    }

    private final String inputGzPath;
    private final String outputSqlPath;

    public OpenFoodFactsSqlGenerator(
        @Value("${canmakan.etl.open-food-facts.input-gz-path:}") String inputGzPath,
        @Value("${canmakan.etl.open-food-facts.output-sql-path:}") String outputSqlPath
    ) {
        this.inputGzPath = inputGzPath;
        this.outputSqlPath = outputSqlPath;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Singapore Products Batch SQL Export...");

        if (inputGzPath.isBlank() || outputSqlPath.isBlank()) {
            log.error("Set canmakan.etl.open-food-facts.input-gz-path and output-sql-path "
                + "(env: OFF_INPUT_GZ_PATH / OFF_OUTPUT_SQL_PATH) before running the 'generate-sql' profile.");
            return;
        }

        int totalCount = 0;
        List<String> valueTuples = new ArrayList<>();

        try (
            GZIPInputStream gzipStream = new GZIPInputStream(new FileInputStream(inputGzPath));
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputSqlPath), StandardCharsets.UTF_8))
        ) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            Map<Column, Integer> columnIndexes = resolveColumnIndexes(headerLine.split("\t"));

            String line;
            while ((line = reader.readLine()) != null) {
                totalCount += processLine(line, columnIndexes, writer, valueTuples);
            }

            // Flush remaining rows
            if (!valueTuples.isEmpty()) {
                writeBatch(writer, valueTuples);
            }

            writer.flush();
            log.info("Finished! Total products written in batches: {}", totalCount);

        } catch (Exception e) {
            log.error("Failed to generate SQL export from Open Food Facts data", e);
        }
    }

    private static void writeBatch(BufferedWriter writer, List<String> tuples) throws IOException {
        writer.write(INSERT_HEADER);
        writer.write(String.join(",\n", tuples));
        writer.write(ON_DUPLICATE_FOOTER);
    }

    private static Map<Column, Integer> resolveColumnIndexes(String[] headers) {
        Map<Column, Integer> columnIndexes = new EnumMap<>(Column.class);
        for (Column column : Column.values()) {
            columnIndexes.put(column, findIndex(headers, column.headerName));
        }
        if (columnIndexes.get(Column.NUTRITION_GRADE) == -1) {
            columnIndexes.put(Column.NUTRITION_GRADE, findIndex(headers, "nutrition_grade_fr"));
        }
        return columnIndexes;
    }

    /** Parses one CSV line and appends it to the batch if it is a Singapore product with a usable barcode/name. */
    private static int processLine(String line, Map<Column, Integer> columnIndexes, BufferedWriter writer, List<String> valueTuples)
        throws IOException {
        if (!line.contains("singapore")) {
            return 0;
        }

        String[] cols = line.split("\t", -1);
        if (!isSingaporeProduct(cols, columnIndexes)) {
            return 0;
        }

        String barcode = truncate(getValue(cols, columnIndexes.get(Column.CODE)), 50);
        String productName = truncate(getValue(cols, columnIndexes.get(Column.PRODUCT_NAME)), 255);
        if (barcode.isEmpty() || productName.isEmpty()) {
            return 0;
        }

        valueTuples.add(buildInsertTuple(cols, columnIndexes, barcode, productName));

        // Flush batch every 500 rows
        if (valueTuples.size() >= BATCH_SIZE) {
            writeBatch(writer, valueTuples);
            valueTuples.clear();
        }
        return 1;
    }

    private static boolean isSingaporeProduct(String[] cols, Map<Column, Integer> columnIndexes) {
        int countriesTagsIndex = columnIndexes.get(Column.COUNTRIES_TAGS);
        if (countriesTagsIndex == -1 || countriesTagsIndex >= cols.length) {
            return false;
        }
        return cols[countriesTagsIndex].toLowerCase().contains("singapore");
    }

    // Build tuple: ('001', 'Name', NULL, ...)
    private static String buildInsertTuple(String[] cols, Map<Column, Integer> idx, String barcode, String productName) {
        return String.format(
            "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
            formatSqlString(barcode),
            formatSqlString(productName),
            formatSqlString(truncate(getValue(cols, idx.get(Column.GENERIC_NAME)), 255)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.BRAND)), 255)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.QUANTITY)), 100)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.SERVING_SIZE)), 100)),
            formatSqlDecimal(getValue(cols, idx.get(Column.SERVING_QUANTITY))),
            formatSqlString(getValue(cols, idx.get(Column.CATEGORIES))),
            formatSqlString(getValue(cols, idx.get(Column.CATEGORY_TAGS))),
            formatSqlString(truncate(getValue(cols, idx.get(Column.MAIN_CATEGORY)), 1000)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.MAIN_CATEGORY_EN)), 1000)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.FOOD_GROUPS)), 1000)),
            formatSqlString(getValue(cols, idx.get(Column.FOOD_GROUPS_TAGS))),
            formatSqlString(getValue(cols, idx.get(Column.INGREDIENTS_TEXT))),
            formatSqlString(getValue(cols, idx.get(Column.INGREDIENTS_ANALYSIS_TAGS))),
            formatSqlString(getValue(cols, idx.get(Column.ALLERGENS))),
            formatSqlString(getValue(cols, idx.get(Column.ALLERGENS_EN))),
            formatSqlString(getValue(cols, idx.get(Column.TRACES_TAGS))),
            formatSqlString(getValue(cols, idx.get(Column.TRACES_EN))),
            formatSqlString(getValue(cols, idx.get(Column.LABELS_TAGS))),
            formatSqlString(getValue(cols, idx.get(Column.LABELS_EN))),
            formatSqlString(getValue(cols, idx.get(Column.COUNTRIES_TAGS))),
            formatSqlString(getValue(cols, idx.get(Column.IMAGE_URL))),
            formatSqlString(truncate(getValue(cols, idx.get(Column.NUTRITION_GRADE)), 10)),
            formatSqlString(truncate(getValue(cols, idx.get(Column.NO_NUTRITION_DATA)), 10)),
            formatSqlDecimal(getValue(cols, idx.get(Column.COMPLETENESS))),
            formatSqlDecimal(getValue(cols, idx.get(Column.ENERGY_KCAL))),
            formatSqlDecimal(getValue(cols, idx.get(Column.ENERGY_KJ))),
            formatSqlDecimal(getValue(cols, idx.get(Column.SUGARS))),
            formatSqlDecimal(getValue(cols, idx.get(Column.ADDED_SUGARS))),
            formatSqlDecimal(getValue(cols, idx.get(Column.PROTEINS))),
            formatSqlDecimal(getValue(cols, idx.get(Column.CARBOHYDRATES))),
            formatSqlDecimal(getValue(cols, idx.get(Column.FAT))),
            formatSqlDecimal(getValue(cols, idx.get(Column.SATURATED_FAT))),
            formatSqlDecimal(getValue(cols, idx.get(Column.TRANS_FAT))),
            formatSqlDecimal(getValue(cols, idx.get(Column.CHOLESTEROL))),
            formatSqlDecimal(getValue(cols, idx.get(Column.FIBER))),
            formatSqlDecimal(getValue(cols, idx.get(Column.SODIUM))),
            formatSqlDecimal(getValue(cols, idx.get(Column.SALT))),
            formatSqlDecimal(getValue(cols, idx.get(Column.ADDED_SALT))),
            formatSqlDecimal(getValue(cols, idx.get(Column.ALCOHOL)))
        );
    }

    private static int findIndex(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(colName)) return i;
        }
        return -1;
    }

    private static String getValue(String[] cols, int index) {
        return (index != -1 && index < cols.length) ? cols[index].trim() : "";
    }

    private static String truncate(String val, int maxLength) {
        if (val == null) return "";
        return val.length() > maxLength ? val.substring(0, maxLength) : val;
    }

    private static String formatSqlString(String val) {
        if (val == null || val.trim().isEmpty()) return "NULL";
        return "'" + val.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static String formatSqlDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return "NULL";
        try {
            return String.valueOf(Double.parseDouble(val.trim()));
        } catch (NumberFormatException e) {
            return "NULL";
        }
    }
}
