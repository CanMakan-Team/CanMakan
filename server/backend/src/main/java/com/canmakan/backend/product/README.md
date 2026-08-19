# product

Core product assessment flow.

## Purpose
Handles everything related to scanning a product and producing a safety result.

## Responsibilities
- Barcode scanning intake
- Ingredient list (OCR) intake
- Product lookup (via `integration`)
- Safety verdict generation (Safe / Warning / Unsafe)
- Two-step scan API: `POST /api/scan/validate` (OFF + EAN is-food check), then `POST /api/scan/assess` (OFF fetch + verdict)
- Alternative product recommendations (UC5 Tier A catalog + Tier C content-based ranking; LLM not on the MVP path)
- Scan history
- User reporting of incorrect product information

## Internal structure (multi-capability domain)

Capability sub-slices keep related code together. **Do not** mass-move types into nested `dto/` trees for packaging symmetry (F19 / P5).

```
product/
  scan/
  assessment/
  recommendation/   # capability folders: catalog, filter, ranking, discovery, history, dto
  model/            # ScanProduct (history projection of products)
  …
```

### Dual read models of `products` (intentional)

| Type | Package | Role |
| --- | --- | --- |
| `ScanProduct` | `product.model` | Narrow mapping for scan-history / barcode display |
| `CatalogProduct` | `product.recommendation.catalog` | Richer mapping for UC5 Tier A matching and rule checks |

Both map the same `products` table with different column sets. They are **not** candidates for a single merged JPA entity: each slice should only load the fields it needs.

This is the heart of the Core MVP.
