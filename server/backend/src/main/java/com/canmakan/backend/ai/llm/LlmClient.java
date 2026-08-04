package com.canmakan.backend.ai.llm;

import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.FindingType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Executes a bounded synchronous Spring AI request and parses model evidence.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class LlmClient {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Duration responseTimeout;
    private final int retryCount;
    private final Duration retryBackoff;
    private final String configuredModelId;
    private final ExecutorService executor;

    public LlmClient(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper,
            @Value("${app.ai.response-timeout:30s}") Duration responseTimeout,
            @Value("${app.ai.retry-count:2}") int retryCount,
            @Value("${app.ai.retry-backoff:500ms}") Duration retryBackoff,
            @Value("${spring.ai.openai.chat.model:unconfigured}") String configuredModelId
    ) {
        this(
                chatModelProvider.getIfAvailable(),
                objectMapper,
                responseTimeout,
                retryCount,
                retryBackoff,
                configuredModelId,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    LlmClient(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            Duration responseTimeout,
            int retryCount,
            Duration retryBackoff,
            String configuredModelId,
            ExecutorService executor
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.responseTimeout = responseTimeout == null || responseTimeout.isNegative()
                || responseTimeout.isZero() ? Duration.ofSeconds(30) : responseTimeout;
        this.retryCount = Math.max(0, retryCount);
        this.retryBackoff = retryBackoff == null || retryBackoff.isNegative()
                ? Duration.ZERO : retryBackoff;
        this.configuredModelId = isBlank(configuredModelId) ? "unconfigured" : configuredModelId;
        this.executor = executor;
    }

    /**
     * Runs one structured evidence assessment. The returned result never contains
     * an authoritative final dietary verdict.
     */
    public LlmAssessmentResult assess(String compiledPrompt) {
        long startedAt = System.nanoTime();
        RequestMetadata requestMetadata = readRequestMetadata(compiledPrompt);
        if (chatModel == null) {
            return failure(
                    LlmAssessmentResult.Status.PROVIDER_UNAVAILABLE,
                    "No model provider is configured.",
                    compiledPrompt,
                    requestMetadata,
                    elapsedMillis(startedAt)
            );
        }
        if (isBlank(compiledPrompt)) {
            return failure(
                    LlmAssessmentResult.Status.INVALID_RESPONSE,
                    "The compiled model request is empty.",
                    compiledPrompt,
                    requestMetadata,
                    elapsedMillis(startedAt)
            );
        }

        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                ChatResponse response = callWithTimeout(compiledPrompt);
                return mapResponse(
                        response,
                        compiledPrompt,
                        requestMetadata,
                        elapsedMillis(startedAt)
                );
            } catch (TimeoutException exception) {
                if (attempt < retryCount) {
                    if (!pauseBeforeRetry(attempt + 1)) {
                        return interrupted(compiledPrompt, requestMetadata, startedAt);
                    }
                    continue;
                }
                return failure(
                        LlmAssessmentResult.Status.TIMEOUT,
                        "The model provider did not respond within the configured timeout.",
                        compiledPrompt,
                        requestMetadata,
                        elapsedMillis(startedAt)
                );
            } catch (ToolExecutionException exception) {
                return failure(
                        LlmAssessmentResult.Status.TOOL_ERROR,
                        "A model tool call failed.",
                        compiledPrompt,
                        requestMetadata,
                        elapsedMillis(startedAt)
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return interrupted(compiledPrompt, requestMetadata, startedAt);
            } catch (RuntimeException exception) {
                if (attempt < retryCount && isTransient(exception)) {
                    if (!pauseBeforeRetry(attempt + 1)) {
                        return interrupted(compiledPrompt, requestMetadata, startedAt);
                    }
                    continue;
                }
                return failure(
                        LlmAssessmentResult.Status.PROVIDER_ERROR,
                        "The model provider request failed ("
                                + exception.getClass().getSimpleName() + ").",
                        compiledPrompt,
                        requestMetadata,
                        elapsedMillis(startedAt)
                );
            }
        }

        return failure(
                LlmAssessmentResult.Status.PROVIDER_ERROR,
                "The model provider request failed.",
                compiledPrompt,
                requestMetadata,
                elapsedMillis(startedAt)
        );
    }

    private ChatResponse callWithTimeout(String compiledPrompt)
            throws TimeoutException, InterruptedException {
        Future<ChatResponse> future = executor.submit(
                () -> chatModel.call(new Prompt(compiledPrompt))
        );
        try {
            return future.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("The model provider request failed.", cause);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    private LlmAssessmentResult mapResponse(
            ChatResponse response,
            String compiledPrompt,
            RequestMetadata requestMetadata,
            long latencyMs
    ) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return failure(
                    LlmAssessmentResult.Status.INVALID_RESPONSE,
                    "The model provider returned no structured response.",
                    compiledPrompt,
                    requestMetadata,
                    latencyMs
            );
        }

        String rawResponse = response.getResult().getOutput().getText();
        StructuredEvidence evidence;
        try {
            evidence = objectMapper.readValue(rawResponse, StructuredEvidence.class);
        } catch (JsonProcessingException exception) {
            return failureWithRawResponse(
                    "The model provider returned invalid structured JSON.",
                    compiledPrompt,
                    rawResponse,
                    requestMetadata,
                    latencyMs
            );
        }

        if (evidence == null || isBlank(evidence.explanation())
                || invalidConfidence(evidence.confidence())) {
            return failureWithRawResponse(
                    "The model provider response failed schema validation.",
                    compiledPrompt,
                    rawResponse,
                    requestMetadata,
                    latencyMs
            );
        }

        List<Finding> proposedFindings = safeList(evidence.proposedFindings()).stream()
                .filter(finding -> finding != null && !isBlank(finding.reason()))
                .map(finding -> new Finding(
                        finding.restrictionCode(),
                        finding.ingredientName(),
                        finding.reason(),
                        FindingType.MODEL_EVIDENCE
                ))
                .toList();
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        List<String> toolCalls = new ArrayList<>(safeStrings(evidence.toolCalls()));
        if (response.hasToolCalls()) {
            toolCalls.add("Provider response included a tool call.");
        }

        return new LlmAssessmentResult(
                proposedFindings,
                safeStrings(evidence.unresolvedIngredients()),
                safeMap(evidence.resolvedNames()),
                evidence.confidence(),
                evidence.explanation(),
                metadata == null || isBlank(metadata.getModel())
                        ? configuredModelId : metadata.getModel(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                latencyMs,
                requestMetadata.promptVersion(),
                requestMetadata.correlationId(),
                toolCalls,
                compiledPrompt,
                rawResponse,
                LlmAssessmentResult.Status.SUCCESS,
                null
        );
    }

    private LlmAssessmentResult failureWithRawResponse(
            String message,
            String compiledPrompt,
            String rawResponse,
            RequestMetadata metadata,
            long latencyMs
    ) {
        return new LlmAssessmentResult(
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                configuredModelId,
                null,
                null,
                null,
                latencyMs,
                metadata.promptVersion(),
                metadata.correlationId(),
                List.of(),
                compiledPrompt,
                rawResponse,
                LlmAssessmentResult.Status.INVALID_RESPONSE,
                message
        );
    }

    private LlmAssessmentResult failure(
            LlmAssessmentResult.Status status,
            String message,
            String compiledPrompt,
            RequestMetadata metadata,
            long latencyMs
    ) {
        return new LlmAssessmentResult(
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                configuredModelId,
                null,
                null,
                null,
                latencyMs,
                metadata.promptVersion(),
                metadata.correlationId(),
                List.of(),
                compiledPrompt,
                null,
                status,
                message
        );
    }

    private LlmAssessmentResult interrupted(
            String compiledPrompt,
            RequestMetadata metadata,
            long startedAt
    ) {
        return failure(
                LlmAssessmentResult.Status.INTERRUPTED,
                "The model provider request was interrupted.",
                compiledPrompt,
                metadata,
                elapsedMillis(startedAt)
        );
    }

    private RequestMetadata readRequestMetadata(String compiledPrompt) {
        if (isBlank(compiledPrompt)) {
            return new RequestMetadata(PromptBuilder.PROMPT_VERSION, "unassigned");
        }
        try {
            JsonNode root = objectMapper.readTree(compiledPrompt);
            return new RequestMetadata(
                    root.path("promptVersion").asText(PromptBuilder.PROMPT_VERSION),
                    root.path("correlationId").asText("unassigned")
            );
        } catch (JsonProcessingException ignored) {
            return new RequestMetadata(PromptBuilder.PROMPT_VERSION, "unassigned");
        }
    }

    private boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ResourceAccessException
                    || current instanceof HttpServerErrorException
                    || current instanceof HttpClientErrorException.TooManyRequests
                    || current instanceof SocketTimeoutException
                    || current instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private boolean pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBackoff.multipliedBy(attempt).toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean invalidConfidence(BigDecimal confidence) {
        return confidence == null
                || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> safeStrings(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> values) {
        if (values == null) {
            return Map.of();
        }
        Map<K, V> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PreDestroy
    void closeExecutor() {
        executor.close();
    }

    private record RequestMetadata(String promptVersion, String correlationId) {
    }

    private record StructuredEvidence(
            List<ProposedFinding> proposedFindings,
            List<String> unresolvedIngredients,
            Map<String, String> resolvedNames,
            BigDecimal confidence,
            String explanation,
            List<String> toolCalls
    ) {
    }

    private record ProposedFinding(
            String restrictionCode,
            String ingredientName,
            String reason
    ) {
    }
}
