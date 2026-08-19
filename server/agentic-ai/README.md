# Agentic AI (reserved)

This directory is **not** a deployable. CanMakan’s assess agent runs **in-process** in Spring Boot:

- Dietary tools: [`server/backend/.../knowledgebase/mcp`](../backend/src/main/java/com/canmakan/backend/knowledgebase/README.md)
- Tier-3 LLM evidence: [`server/backend/.../ai`](../backend/src/main/java/com/canmakan/backend/ai/README.md)
- Orchestration: [`AssessmentOrchestrator`](../backend/src/main/java/com/canmakan/backend/product/assessment/AssessmentOrchestrator.java)

Architecture: [`docs/architecture/mcp-agent-architecture.md`](../../docs/architecture/mcp-agent-architecture.md).

Do not add a second agent runtime here without an explicit team decision. Default assess is Tier-1 rules (`CANMAKAN_AI_ENABLED=false`).
