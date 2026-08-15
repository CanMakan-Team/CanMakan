# productscan

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

## Internal structure suggestion
- `scan/`
- `verdict/`
- `recommendation/`
- `history/`
- `reporting/`

This is the heart of the Core MVP.