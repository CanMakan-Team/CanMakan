# product/history

Scan history (UC4 personal list).

## Responsibilities
- List previous scans for the active dietary profile
- Allow re-opening a past verdict
- Optional local caching (Room)

## API
Live: `GET /api/scan/history/{profileId}` via `ScanHistoryApiService`
(backed by backend `ScanController` + `ScanHistoryService`; JWT + profile authz required).

History loads only for a positive active profile whose owning account matches
the current authenticated user. Profile/account changes clear the old list and
stale responses are ignored. Profile-less users can open History in the normal
shell; the ViewModel exposes a non-error setup-required state, the page shows a
setup action, and no history request is made.
