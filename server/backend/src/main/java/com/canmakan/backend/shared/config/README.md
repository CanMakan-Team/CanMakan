# shared/config

Cross-cutting Spring beans that are not security or AI.

## Purpose

HTTP client and JPA setup only. No business logic.

## Contents

| File | Role |
| --- | --- |
| [`HttpClientConfig.java`](HttpClientConfig.java) | Shared `WebClient` / HTTP beans used by [`integration`](../../integration/README.md) |
| [`JpaConfig.java`](JpaConfig.java) | JPA auditing / entity config |

JWT, CORS, and the filter chain live in [`shared/security`](../security/README.md). LLM `ChatClient` beans live in [`ai/llm/LlmChatClientConfig.java`](../../ai/llm/LlmChatClientConfig.java). Refresh-token properties live in [`auth/config`](../../auth/README.md).
