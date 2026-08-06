# product/scan

Product scanning entry point.

## Responsibilities
- Camera permission handling
- Barcode scanning (ML Kit)
- Ingredient list OCR (ML Kit) when barcode is unavailable
- Two-step backend calls: `POST /api/scan/validate` then `POST /api/scan/assess`
- Navigating to verdict screen
