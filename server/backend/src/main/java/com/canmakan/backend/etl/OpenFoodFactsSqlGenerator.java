package com.canmakan.backend.etl;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

//NOTE: run only to extract data from Open food fact database (csv zipped file to be saved on local folder)
@Component
@Profile("generate-sql")
public class OpenFoodFactsSqlGenerator implements CommandLineRunner {

	private static final String INPUT_GZ_PATH = "C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/en.openfoodfacts.org.products.csv.gz";
    private static final String OUTPUT_SQL_PATH = "C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/singapore_products.sql";

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

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting Singapore Products Batch SQL Export...");

        int totalCount = 0;
        List<String> valueTuples = new ArrayList<>();

        try (
            GZIPInputStream gzipStream = new GZIPInputStream(new FileInputStream(INPUT_GZ_PATH));
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_SQL_PATH), StandardCharsets.UTF_8))
        ) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split("\t");

            // --- Column Index Mapping ---
            int codeIdx = findIndex(headers, "code");
            int nameIdx = findIndex(headers, "product_name");
            int genericNameIdx = findIndex(headers, "generic_name");
            int brandIdx = findIndex(headers, "brands");
            int quantityIdx = findIndex(headers, "quantity");
            int servingSizeIdx = findIndex(headers, "serving_size");
            int servingQuantityIdx = findIndex(headers, "serving_quantity");
            int categoriesIdx = findIndex(headers, "categories");
            int categoryTagsIdx = findIndex(headers, "categories_tags");
            int mainCategoryIdx = findIndex(headers, "main_category");
            int mainCategoryEnIdx = findIndex(headers, "main_category_en");
            int foodGroupsIdx = findIndex(headers, "food_groups");
            int foodGroupsTagsIdx = findIndex(headers, "food_groups_tags");
            int ingredientsTextIdx = findIndex(headers, "ingredients_text");
            int ingredientsAnalysisTagsIdx = findIndex(headers, "ingredients_analysis_tags");
            int allergensIdx = findIndex(headers, "allergens");
            int allergensEnIdx = findIndex(headers, "allergens_en");
            int tracesTagsIdx = findIndex(headers, "traces_tags");
            int tracesEnIdx = findIndex(headers, "traces_en");
            int labelsTagsIdx = findIndex(headers, "labels_tags");
            int labelsEnIdx = findIndex(headers, "labels_en");
            int countriesTagsIdx = findIndex(headers, "countries_tags");
            int imageIdx = findIndex(headers, "image_url");

            int nutritionGradeIdx = findIndex(headers, "nutriscore_grade");
            if (nutritionGradeIdx == -1) nutritionGradeIdx = findIndex(headers, "nutrition_grade_fr");
            int noNutritionDataIdx = findIndex(headers, "no_nutrition_data");
            int completenessIdx = findIndex(headers, "completeness");

            int energyKcalIdx = findIndex(headers, "energy-kcal_100g");
            int energyKjIdx = findIndex(headers, "energy_100g");
            int sugarsIdx = findIndex(headers, "sugars_100g");
            int addedSugarsIdx = findIndex(headers, "added-sugars_100g");
            int proteinsIdx = findIndex(headers, "proteins_100g");
            int carbohydratesIdx = findIndex(headers, "carbohydrates_100g");
            int fatIdx = findIndex(headers, "fat_100g");
            int saturatedFatIdx = findIndex(headers, "saturated-fat_100g");
            int transFatIdx = findIndex(headers, "trans-fat_100g");
            int cholesterolIdx = findIndex(headers, "cholesterol_100g");
            int fiberIdx = findIndex(headers, "fiber_100g");
            int sodiumIdx = findIndex(headers, "sodium_100g");
            int saltIdx = findIndex(headers, "salt_100g");
            int addedSaltIdx = findIndex(headers, "added-salt_100g");
            int alcoholIdx = findIndex(headers, "alcohol_100g");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("en:singapore") || line.contains("singapore")) {
                    String[] cols = line.split("\t", -1);

                    if (countriesTagsIdx != -1 && countriesTagsIdx < cols.length) {
                        String countries = cols[countriesTagsIdx].toLowerCase();
                        if (countries.contains("singapore") || countries.contains("en:singapore")) {

                            String barcode = truncate(getValue(cols, codeIdx), 50);
                            String productName = truncate(getValue(cols, nameIdx), 255);

                            if (barcode.isEmpty() || productName.isEmpty()) continue;

                            // Build tuple: ('001', 'Name', NULL, ...)
                            String tuple = String.format("(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                                formatSqlString(barcode),
                                formatSqlString(productName),
                                formatSqlString(truncate(getValue(cols, genericNameIdx), 255)),
                                formatSqlString(truncate(getValue(cols, brandIdx), 255)),
                                formatSqlString(truncate(getValue(cols, quantityIdx), 100)),
                                formatSqlString(truncate(getValue(cols, servingSizeIdx), 100)),
                                formatSqlDecimal(getValue(cols, servingQuantityIdx)),
                                formatSqlString(getValue(cols, categoriesIdx)),
                                formatSqlString(getValue(cols, categoryTagsIdx)),
                                formatSqlString(truncate(getValue(cols, mainCategoryIdx), 1000)),
                                formatSqlString(truncate(getValue(cols, mainCategoryEnIdx), 1000)),
                                formatSqlString(truncate(getValue(cols, foodGroupsIdx), 1000)),
                                formatSqlString(getValue(cols, foodGroupsTagsIdx)),
                                formatSqlString(getValue(cols, ingredientsTextIdx)),
                                formatSqlString(getValue(cols, ingredientsAnalysisTagsIdx)),
                                formatSqlString(getValue(cols, allergensIdx)),
                                formatSqlString(getValue(cols, allergensEnIdx)),
                                formatSqlString(getValue(cols, tracesTagsIdx)),
                                formatSqlString(getValue(cols, tracesEnIdx)),
                                formatSqlString(getValue(cols, labelsTagsIdx)),
                                formatSqlString(getValue(cols, labelsEnIdx)),
                                formatSqlString(getValue(cols, countriesTagsIdx)),
                                formatSqlString(getValue(cols, imageIdx)),
                                formatSqlString(truncate(getValue(cols, nutritionGradeIdx), 10)),
                                formatSqlString(truncate(getValue(cols, noNutritionDataIdx), 10)),
                                formatSqlDecimal(getValue(cols, completenessIdx)),
                                formatSqlDecimal(getValue(cols, energyKcalIdx)),
                                formatSqlDecimal(getValue(cols, energyKjIdx)),
                                formatSqlDecimal(getValue(cols, sugarsIdx)),
                                formatSqlDecimal(getValue(cols, addedSugarsIdx)),
                                formatSqlDecimal(getValue(cols, proteinsIdx)),
                                formatSqlDecimal(getValue(cols, carbohydratesIdx)),
                                formatSqlDecimal(getValue(cols, fatIdx)),
                                formatSqlDecimal(getValue(cols, saturatedFatIdx)),
                                formatSqlDecimal(getValue(cols, transFatIdx)),
                                formatSqlDecimal(getValue(cols, cholesterolIdx)),
                                formatSqlDecimal(getValue(cols, fiberIdx)),
                                formatSqlDecimal(getValue(cols, sodiumIdx)),
                                formatSqlDecimal(getValue(cols, saltIdx)),
                                formatSqlDecimal(getValue(cols, addedSaltIdx)),
                                formatSqlDecimal(getValue(cols, alcoholIdx))
                            );

                            valueTuples.add(tuple);
                            totalCount++;

                            // Flush batch every 500 rows
                            if (valueTuples.size() >= BATCH_SIZE) {
                                writeBatch(writer, valueTuples);
                                valueTuples.clear();
                            }
                        }
                    }
                }
            }

            // Flush remaining rows
            if (!valueTuples.isEmpty()) {
                writeBatch(writer, valueTuples);
            }

            writer.flush();
            System.out.println("Finished! Total products written in batches: " + totalCount);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeBatch(BufferedWriter writer, List<String> tuples) throws Exception {
        writer.write(INSERT_HEADER);
        writer.write(String.join(",\n", tuples));
        writer.write(ON_DUPLICATE_FOOTER);
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
