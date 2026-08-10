# product/history

Scan history (UC4 personal list).

## Responsibilities
- List previous scans for the active dietary profile
- Allow re-opening a past verdict
- Optional local caching (Room)

## API
Live: `GET /api/scan/history/{profileId}` via `ScanHistoryApiService`
(backed by backend `ScanController` + `ScanHistoryService`; JWT + profile authz required).
