# features/analytics

Trends and usage insights.

## Purpose
Displays aggregated analytics for administrators.

## Contains
- Consumer trends
- Related charts and summary views
- UC22 CSV export of the currently loaded UC7 aggregate response

## Notes
- Aligns with backend `analytics` package
- Admin-only access
- Keep feature-specific; shared chart primitives can live in `shared/ui`
- CSV generation is client-side so it reuses the exact loaded date/category result without a second analytics request.
- Reports contain only UC7's anonymous aggregate fields, include a UTF-8 BOM, escape CSV special characters, and neutralise spreadsheet-formula prefixes.
