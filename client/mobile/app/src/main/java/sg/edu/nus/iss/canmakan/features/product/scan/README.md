# product/scan

Product scanning entry point.

## Responsibilities
- Camera permission handling
- Barcode scanning (ML Kit)
- Ingredient list OCR (ML Kit) when barcode is unavailable
- Two-step backend calls: `POST /api/scan/validate` then `POST /api/scan/assess`
- Navigating to verdict screen

Assessment requires a positive active profile owned by the current authenticated
account. Profile-less users still see the Scanner page, drawer, and bottom
navigation, but the camera/scan action is not started and an in-context **Set up
profile** action opens the shared authenticated profile setup flow. An
account/profile change between validation and assessment cancels the scan and
ignores stale results.
