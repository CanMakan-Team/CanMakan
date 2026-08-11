# navigation

Root navigation of the app.

## Purpose
Wires feature navigation graphs together and defines the top-level `NavHost`.

## Contains
- Root authentication/application composition
- Separate unauthenticated and consumer-main `NavHost` instances
- Top-level routes
- Asynchronous restoration, recovery, unsupported-account and sign-out states
- Navigation helpers & deep links

The auth and main graphs never share a back stack. A successful Login, local
session invalidation, or completed Logout replaces the root composition and
therefore removes the opposite graph and its destinations.

## Does not contain
- Feature screens or ViewModels
- Business logic

Features expose their own navigation APIs; this package only composes them.
