# ai

Tier-3 LLM evidence and AI execution logs. Not a separate deployable.

## Purpose

On WARNING escalation, an in-process Spring AI `ChatClient` may call the same dietary knowledge tools as Tier 1, then return **evidence only**. [`DietaryRuleEngine`](../product/verdict/DietaryRuleEngine.java) still owns SAFE / WARNING / UNSAFE.

Default `CANMAKAN_AI_ENABLED=false` keeps assess on Tier-1 rules. Architecture: [MCP Agent Architecture](../../../../../../../../../docs/architecture/mcp-agent-architecture.md).

## Layout

| Path | Role |
| --- | --- |
| [`llm/LlmClient.java`](llm/LlmClient.java) | Tool-calling evidence agent |
| [`llm/LlmChatClientConfig.java`](llm/LlmChatClientConfig.java) | `ChatClient` beans |
| [`llm/PromptBuilder.java`](llm/PromptBuilder.java) | Assess prompts |
| [`llm/EvidencePayload.java`](llm/EvidencePayload.java) / [`LlmAssessmentResult.java`](llm/LlmAssessmentResult.java) | Evidence DTOs |
| [`log/AiExecutionLog.java`](log/AiExecutionLog.java) | Persistence of assess runs |

Called from [`AssessmentOrchestrator`](../product/assessment/AssessmentOrchestrator.java) via [`LlmEscalationService`](../product/assessment/service/LlmEscalationService.java). Tools live in [`knowledgebase/mcp`](../knowledgebase/README.md).
