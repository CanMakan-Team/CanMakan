# features/dietaryprofile

Individual dietary needs and restrictions.

## Responsibilities
- View and edit the user’s dietary profile
- Manage catalog/preset constraints (Halal, peanut-free, dairy-free, etc.)
- Provide the active dietary profile to the product flow
- After normal client-side login, load the authoritative restriction catalog and create
  the caller's SELF profile through `POST /api/profiles/me`

Custom free-text constraints are not implemented. Supported severities are `STRICT_AVOID` and `INTOLERANCE` (`PREFERENCE` is not accepted by the API).

## Authenticated onboarding

Registration does not fetch restrictions or create a profile. After normal
client-side login, authenticated onboarding loads `GET /api/restrictions` and
creates the SELF profile only through `POST /api/profiles/me`, using the pending
Profile Name and catalog ids with `STRICT_AVOID` or `INTOLERANCE`. Only an exact
`201 Created` response containing a positive profile id switches
`ActiveProfileManager` or clears pending setup. Other 2xx responses and
nonpositive ids are rejected.

An HTTP 409 never creates a duplicate or guesses ownership. Profile creation
failure keeps the account, authenticated session and setup retryable. Every async completion rechecks the initiating
authenticated account before saving, switching, clearing, or continuing.

If setup was previously deferred and no active profile exists, the drawer's
dietary action and the Scanner/History setup actions reopen this one authenticated
flow and ask for a profile name. Deferral leaves the normal authenticated shell
available; it does not create a placeholder or empty backend profile.
Restriction reads and writes that require a profile reject nonpositive ids.
The restriction editor is scoped to the exact `(authenticated account/session,
active profileId)` pair. Logout, account switch, profile switch, and an unset
profile clear selections immediately; cancellation-ignoring stale loads/saves
are discarded after ownership is revalidated.

## Related
Works closely with `family` when a family member profile is active.
