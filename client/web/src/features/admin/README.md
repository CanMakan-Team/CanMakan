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