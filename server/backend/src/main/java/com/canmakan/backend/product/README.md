# product

Scan, assess, verdict, recommendations, and history.

## Purpose

Barcode intake through Open Food Facts, deterministic dietary verdict, optional Tier-3 evidence, and UC5 substitutes. OCR ingredient intake (UC24) is **not** implemented; `AssessmentRequest` is barcode + `profileId` only.

## Two-step scan API

1. `POST /api/scan/validate` — OFF + is-food check ([`ScanController`](scan/ScanController.java))
2. `POST /api/scan/assess` — fetch + verdict ([`AssessmentOrchestrator`](assessment/AssessmentOrchestrator.java))

## Internal structure

```
product/
  scan/           # validate, persist, history, user feedback
  assessment/     # orchestrate OFF + tiers
  verdict/        # DietaryRuleEngine and checkers
  recommendation/ # UC5 catalog / filter / rank
  model/          # ScanProduct history projection
```

| Slice | Important files |
| --- | --- |
| Scan | [`scan/ScanController.java`](scan/ScanController.java), [`scan/ScanService.java`](scan/ScanService.java) |
| Assess | [`assessment/AssessmentOrchestrator.java`](assessment/AssessmentOrchestrator.java), [`assessment/service/LlmEscalationService.java`](assessment/service/LlmEscalationService.java) |
| Verdict | [`verdict/DietaryRuleEngine.java`](verdict/DietaryRuleEngine.java) |
| Recs | [`recommendation/README.md`](recommendation/README.md) |

### Dual read models of `products` (intentional)

| Type | Package | Role |
| --- | --- | --- |
| `ScanProduct` | `product.model` | Narrow mapping for scan-history / barcode display |
| `CatalogProduct` | `product.recommendation.catalog` | Richer mapping for UC5 matching and rule checks |

Both map the same `products` table. They are not candidates for a single merged JPA entity.
