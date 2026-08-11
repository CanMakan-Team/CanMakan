# product/scan

Product scanning entry point.

## Responsibilities
- Camera permission handling
- Barcode scanning (ML Kit)
- Ingredient list OCR (ML Kit) when barcode is unavailable
- Two-step backend calls: `POST /api/scan/validate` then `POST /api/scan/assess`
- Navigating to verdict screen

Assessment requires a positive active profile owned by the current authenticated
account. Profile-less users are gated before validation, and an account/profile
change between validation and assessment cancels the scan and ignores stale
results.
