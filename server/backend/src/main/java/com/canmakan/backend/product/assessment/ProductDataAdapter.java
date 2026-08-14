package com.canmakan.backend.product.assessment;

import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.IngredientLabelParser;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.verdict.ProductData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Adapts a source-neutral product lookup result into the immutable snapshot used
 * by the deterministic verdict engine.
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia
 */
@Service
public class ProductDataAdapter {

    private final BarcodeValidationClient barcodeValidationClient;

    public ProductDataAdapter(BarcodeValidationClient barcodeValidationClient) {
        this.barcodeValidationClient = Objects.requireNonNull(
            barcodeValidationClient, "barcodeValidationClient");
    }

    /**
     * Looks up a product by barcode via Open Food Facts {@code fetchProduct}
     * for the assess step. EAN-Search is used in {@code /api/scan/validate}, not here.
     *
     * @param barcode the scanned barcode
     * @return the source-neutral lookup result
     * 
     * @author YangMaowei
     * @author Amelia
     */
    public ProductLookupResult lookup(String barcode) {
        return barcodeValidationClient.fetchProduct(barcode);
    }

    /**
     * Maps the lookup result without applying dietary rules or interpreting data.
     * The ProductData object is used to store the product data in the database.
     *
     * @param result the source-neutral product lookup result
     * @return the product snapshot expected by the verdict engine
     */
    public ProductData toProductData(ProductLookupResult result) {
        Objects.requireNonNull(result, "result must not be null");

        return new ProductData(
            result.barcode(),
            cleanIngredients(result.ingredients()),
            stripMarkup(result.ingredientsText()),
            toLabelTags(result.labelTags()),
            result.tracesTags(),
            result.nutrition(),
            result.ingredientDataComplete()
        );
    }

    // Open Food Facts wraps allergens in underscores (for example "_milk_") and can
    // carry a language prefix on ingredient ids (for example "en:milk"). Both are
    // source markup, not part of the real ingredient name. They are removed here so
    // the name matches the ingredient alias table during resolution and displays
    // cleanly in the verdict, rather than surfacing as an unresolved "Fresh _milk_".
    private static final Pattern LANGUAGE_PREFIX = Pattern.compile("^[a-z]{2,3}:");

    // Provenance qualifiers never change an ingredient's allergen identity but stop it matching the
    // alias table (e.g. "Non GMO wheat" would not match "Wheat" and its GLUTEN root would be missed).
    private static final Pattern PROVENANCE_QUALIFIER =
        Pattern.compile("(?i)^(?:non[-\\s]?gmo|organic|gmo)\\s+");

    private List<Ingredient> cleanIngredients(List<Ingredient> ingredients) {
        if (ingredients == null) {
            return List.of();
        }
        List<Ingredient> cleaned = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            String name = cleanIngredientName(ingredient.ingredientName());
            if (name.isBlank()) {
                continue;
            }
            if (name.equals(ingredient.ingredientName())) {
                cleaned.add(ingredient);
            } else {
                cleaned.add(new Ingredient(
                    name,
                    ingredient.parentAllergen(),
                    ingredient.rootAllergen(),
                    ingredient.chemicalAlias()));
            }
        }
        return List.copyOf(rejoinSplitParentheticals(cleaned));
    }

    // Naive comma splits (and some OFF leaves) break "Oyster Extract (Oysters, Water, Salt)"
    // into three names. Rejoin those fragments so Water/Salt are not treated as their own labels.
    private static List<Ingredient> rejoinSplitParentheticals(List<Ingredient> ingredients) {
        List<String> names = ingredients.stream().map(Ingredient::ingredientName).toList();
        List<String> mergedNames = IngredientLabelParser.normalize(names);
        if (mergedNames.equals(names)) {
            return ingredients;
        }
        List<Ingredient> rebuilt = new ArrayList<>();
        for (String mergedName : mergedNames) {
            Ingredient exact = null;
            for (Ingredient ingredient : ingredients) {
                if (mergedName.equals(ingredient.ingredientName())) {
                    exact = ingredient;
                    break;
                }
            }
            rebuilt.add(exact != null
                ? exact
                : new Ingredient(mergedName, null, null, false));
        }
        return rebuilt;
    }

    static String cleanIngredientName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String cleaned = rawName.replace("_", "");
        cleaned = LANGUAGE_PREFIX.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        // Strip leading provenance qualifiers, applied repeatedly for stacked ones
        // ("Organic Non GMO wheat" -> "wheat"). Never strips when nothing follows.
        String previous;
        do {
            previous = cleaned;
            cleaned = PROVENANCE_QUALIFIER.matcher(cleaned).replaceFirst("").trim();
        } while (!cleaned.equals(previous));
        return cleaned;
    }

    private static String stripMarkup(String ingredientsText) {
        if (ingredientsText == null) {
            return null;
        }
        return ingredientsText.replace("_", "");
    }

    /**
     * Assemble the {@link ProductData} the engine needs for one barcode.
     * Overloaded method to accept a barcode string instead of a ProductLookupResult.
     * Only maps the lookup result without applying dietary rules or interpreting data.
     *
     * @param barcode the scanned barcode
     * @return a populated {@link ProductData}; {@code dataComplete=false} when partial
     */
    public ProductData toProductData(String barcode) {
        return toProductData(lookup(barcode));
    }

    private List<String> toLabelTags(String labelTags) {
        if (labelTags == null) {
            return List.of();
        }

        return Arrays.stream(labelTags.split(","))
                .map(String::trim)
                .filter(label -> !label.isEmpty())
                .distinct()
                .toList();
    }
}
