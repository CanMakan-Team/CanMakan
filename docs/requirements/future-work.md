# Future work

Product gaps after the current household scan loop. Not a backlog dump.

## 1. Agentic AI is easy to miss in a demo

Tier-3 evidence is implemented in Spring ([`ai/`](../../server/backend/src/main/java/com/canmakan/backend/ai/README.md), [`knowledgebase/mcp`](../../server/backend/src/main/java/com/canmakan/backend/knowledgebase/README.md)), but `CANMAKAN_AI_ENABLED` defaults to false, [`server/agentic-ai/`](../../server/agentic-ai/README.md) is not a deployable, and UC21 (AI reasoning UI) is unassigned.

**Direction:** a documented demo profile with AI on; surface existing `ai_execution_logs` on admin system health (UC21).

## 2. No-barcode path is schema-only (UC24)

[`ocr_scan_results`](../../server/backend/src/main/resources/00_schema.sql) exists. Scan/assess APIs take barcode + profile only. Packs without a barcode cannot be interpreted.

**Direction:** OCR intake into the same validate/assess pipeline, or drop UC24 from the product story.

## 3. Seeded commercial and diet semantics are unfinished

`subscription_*` tables are seeded ([`07_subscriptions_usage.sql`](../../server/backend/src/main/resources/07_subscriptions_usage.sql)) with no API (UC23). `PREFERENCE` severity returns 400. The household scan loop works; monetisation and some diet meanings do not.

**Direction:** ship a minimal plan gate or stop treating unused schema as current product; keep the dietary spec aligned with implemented severities.
