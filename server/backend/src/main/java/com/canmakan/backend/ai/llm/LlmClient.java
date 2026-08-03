package com.canmakan.backend.ai.llm;

import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the Spring AI {@code ChatClient} (e.g. OpenAI gpt-4o). Sends a
 * compiled prompt, parses the structured {@code {verdict, reason}} response, and
 * captures token usage + latency for the audit log.
 *
 * @author XieHuayuan
 */
@Service
public class LlmClient {

    // TODO: inject Spring AI ChatClient (spring-ai-openai-spring-boot-starter).

    /**
     * Run one LLM assessment.
     *
     * @param compiledPrompt the prompt built by {@link PromptBuilder}
     * @return the structured verdict + usage metadata
     */
    public LlmAssessmentResult assess(String compiledPrompt) {
        // TODO: call ChatClient, map to structured output, measure latency + usage.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
