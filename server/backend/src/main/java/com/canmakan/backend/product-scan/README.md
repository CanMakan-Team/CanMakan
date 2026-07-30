# product-scan

Core product assessment flow.

## Purpose
Handles everything related to scanning a product and producing a safety result.

## Responsibilities
- Barcode scanning intake
- Ingredient list (OCR) intake
- Product lookup (via `integration`)
- Safety verdict generation (Safe / Warning / Avoid)
- Alternative product recommendations
- Scan history
- User reporting of incorrect product information

## Internal structure suggestion
- `scan/`
- `verdict/`
- `recommendation/`
- `history/`
- `reporting/`

This is the heart of the Core MVP.