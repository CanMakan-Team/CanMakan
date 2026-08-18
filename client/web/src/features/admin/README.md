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
- User Accounts & Access keeps the existing copy. Filters sit on one row with Apply and Clear, email search applies after a short pause, and the table pages client-side (10 / 25 / 50). The signed-in administrator is omitted from the list. Status changes stay per-account because a reason is required.