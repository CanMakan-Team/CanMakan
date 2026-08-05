package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool: analyse label text (and optional Open Food Facts {@code traces_tags}) for
 * cross-contamination phrases such as "may contain nuts" or
 * "produced in a facility that also processes milk".
 *
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class CrossContaminationTool {

    private final DietaryKnowledgeRepository repository;

    /**
     * Analyse free-text label content and optional structured traces for cross-contamination signals.
     *
     * @param labelText  raw label / ingredients text (may be blank when only traces are provided)
     * @param tracesTags Open Food Facts {@code traces_tags} entries (e.g. {@code en:milk}, {@code en:nuts}), optional
     * @return whether a cross-contamination signal was found and which allergens
     */
    @Tool(
        name = "cross_contamination_analysis",
        description = "Detect cross-contamination phrases (e.g. 'may contain nuts', 'produced in a facility that processes milk') and/or Open Food Facts traces_tags, then extract the mentioned allergens."
    )
    public CrossContaminationResult analyse(
        @ToolParam(description = "Raw label text or ingredients text from the product")
        String labelText,
        @ToolParam(description = "Optional Open Food Facts traces_tags entries, e.g. [en:milk, en:nuts]", required = false)
        List<String> tracesTags) {

        boolean blankLabel = labelText == null || labelText.isBlank();
        boolean blankTraces = tracesTags == null || tracesTags.isEmpty()
                || tracesTags.stream().allMatch(tag -> tag == null || tag.isBlank());
        if (blankLabel && blankTraces) {
            return new CrossContaminationResult(false, List.of(), "");
        }

        // Normalize the label text by trimming and replacing multiple spaces with a single space
        String normalizedLabel = blankLabel ? null : labelText.trim().replaceAll("\\s+", " ");

        return repository.analyseCrossContamination(normalizedLabel, tracesTags)
            .orElseGet(() -> new CrossContaminationResult(false, List.of(), ""));
    }

    /**
     * Convenience overload used by unit tests and direct callers.
     */
    public CrossContaminationResult analyse(String labelText) {
        return analyse(labelText, null);
    }

}
