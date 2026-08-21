package com.canmakan.backend.product.verdict;

/**
 * Formats internal restriction/allergen codes and raw Open Food Facts tags into readable label text
 * for the user-facing verdict messages: no underscores and no {@code en:} language prefixes, so a
 * code like {@code LOW_SUGAR} never reaches the screen verbatim.
 *
 * @author XieHuayuan
 */
final class VerdictText {

    private VerdictText() {
    }

    /**
     * Turns a restriction/allergen code into label text, e.g. {@code "LOW_SUGAR" -> "LOW SUGAR"},
     * {@code "TREE_NUT" -> "TREE NUT"}. {@code null} becomes an empty string.
     */
    static String humanizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Cleans an Open Food Facts tag for display, e.g. {@code "en:tree-nuts" -> "tree nuts"}. */
    static String humanizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return humanizeCode(tag.replaceAll("(?i)\\ben:", ""));
    }

    /**
     * Cleans a cross-contamination phrase for display: strips {@code en:} prefixes, turns
     * underscores into spaces (e.g. {@code traces_tags} -> {@code traces tags}) and tidies commas.
     */
    static String humanizePhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return "";
        }
        String[] parts = phrase.split(",", -1);
        StringBuilder cleaned = new StringBuilder();
        for (String part : parts) {
            String label = humanizeTag(part);
            if (label.isEmpty()) {
                continue;
            }
            if (!cleaned.isEmpty()) {
                cleaned.append(", ");
            }
            cleaned.append(label);
        }
        return cleaned.toString();
    }
}
