package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool: analyse label text for cross-contamination phrases such as
 * "may contain nuts" or "produced in a facility that also processes milk".
 *
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class CrossContaminationTool {

    private final DietaryKnowledgeRepository repository;

    /**
     * Analyse free-text label content for cross-contamination signals.
     * 
     * @param labelText the raw label / ingredients text
     * @return whether a cross-contamination phrase was found and which allergens
     */
    @Tool(name = "cross_contamination_analyse", description = "Detect cross-contamination phrases (e.g. 'may contain nuts', 'produced in a facility that processes milk') and extract the mentioned allergens.")
    public CrossContaminationResult analyse(
        @ToolParam(description = "Raw label text, ingredients text, or traces text from the product") String labelText) {

        if (labelText == null || labelText.isBlank()) {
            return new CrossContaminationResult(false, java.util.List.of(), "");
        }

        // Light normalisation before sending to the repository
        String normalized = labelText.trim().replaceAll("\\s+", " ");

        return repository.analyseCrossContamination(normalized)
            .orElseGet(() -> new CrossContaminationResult(false, java.util.List.of(), ""));
    
        }
}
