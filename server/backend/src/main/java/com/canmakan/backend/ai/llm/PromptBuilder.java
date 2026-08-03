package com.canmakan.backend.ai.llm;

import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the compiled prompt (system + user) sent to the LLM when the rule engine
 * escalates. The compiled prompt is also persisted to
 * {@code ai_execution_logs.compiled_prompt}.
 *
 * @author XieHuayuan
 */
@Service
public class PromptBuilder {

    /**
     * Compose the prompt from the product snapshot and the profile's active rules.
     *
     * @return the compiled prompt (JSON string of system/user messages)
     */
    public String build(ProductData product, List<RestrictionRule> rules) {
        // TODO: render system role + ingredient list + rules into the prompt template.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
