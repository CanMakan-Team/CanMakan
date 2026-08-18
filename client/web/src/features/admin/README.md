# features/admin

System administration features.

## Purpose
Supports administrative management of the platform (web-only).

## Contains
- User accounts and access management
- System dashboard
- Future features / placeholders
- Related admin UI and logic

## Notes
- Protected by admin-level roles
- Aligns with backend `admin` package
- Analytics/trends belong in `features/analytics`, not here
- User Accounts & Access keeps the existing copy. Email, role and status filters apply as they change; Clear filters resets them in one click. The table pages client-side (10 / 25 / 50). The signed-in administrator is omitted from the list. Status changes stay per-account because a reason is required.
- Handle User Feedback uses a 3-column filter grid. Filters and keyword search apply as they change; Clear filters restores the defaults. Long comments open a modal and show a hover preview. Empty comments are muted. Resolve and Unresolve are equal-width action buttons. Submission dates use relative labels, with the exact timestamp on hover.
- System Health keeps the existing copy. Component health uses a 3-column status grid. Admin activity is a Timestamp / User / Action / Target table with admin and event filters. Latency trend sits in an outlined chart (empty state when there are no AI calls). Refresh reloads the snapshot for the selected reporting window.