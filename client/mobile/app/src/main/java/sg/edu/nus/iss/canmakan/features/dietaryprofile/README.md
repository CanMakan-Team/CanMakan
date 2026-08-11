# features/dietaryprofile

Individual dietary needs and restrictions.

## Responsibilities
- View and edit the user’s dietary profile
- Manage preset constraints (Halal, peanut-free, dairy-free, etc.)
- Manage custom constraints
- Provide the active dietary profile to the product flow
- After explicit Login, load the authoritative restriction catalog and create
  the caller's SELF profile through `POST /api/profiles/me`

## Authenticated onboarding

Registration does not fetch restrictions or create a profile. When requested,
authenticated onboarding loads `GET /api/restrictions`, submits only catalog ids
with `STRICT_AVOID` or `INTOLERANCE`, and switches `ActiveProfileManager` only
after an exact `201 Created` response containing a positive profile id, or safe
resolution of an existing SELF profile. Names are nonblank and at most 100
characters. Other 2xx responses and nonpositive ids are rejected.

An HTTP 409 never creates a duplicate or guesses ownership. Family
`selfProfileId` is authoritative when available; otherwise the standalone active
profile must explicitly be `SELF`. The selected restrictions are saved to that
positive existing profile before the app switches it or clears pending setup.
Failure keeps setup retryable. Every async completion rechecks the initiating
authenticated account before saving, switching, clearing, or continuing.

If setup was previously deferred and no active profile exists, the drawer's
dietary action reopens this authenticated flow and asks for a profile name.
Restriction reads and writes that require a profile reject nonpositive ids.
The restriction editor is scoped to the exact `(authenticated account/session,
active profileId)` pair. Logout, account switch, profile switch, and an unset
profile clear selections immediately; cancellation-ignoring stale loads/saves
are discarded after ownership is revalidated.

## Related
Works closely with `family` when a family member profile is active.
