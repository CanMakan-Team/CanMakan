# product/scan

Product scanning entry point.

## Responsibilities
- Camera permission handling
- Barcode scanning (ML Kit + CameraX); see [`BarcodeAnalyzer.kt`](BarcodeAnalyzer.kt)
- Two-step backend calls: `POST /api/scan/validate` then `POST /api/scan/assess` via [`ScannerViewModel.kt`](ScannerViewModel.kt)
- Navigating to the verdict screen

Ingredient-list OCR (UC24) is not implemented. Scan requests send barcode + profile id only.

Assessment requires a positive active profile owned by the current authenticated
account. Profile-less users still see the Scanner page, drawer, and bottom
navigation, but the camera/scan action is not started and an in-context **Set up
profile** action opens the shared authenticated profile setup flow. An
account/profile change between validation and assessment cancels the scan and
ignores stale results.
