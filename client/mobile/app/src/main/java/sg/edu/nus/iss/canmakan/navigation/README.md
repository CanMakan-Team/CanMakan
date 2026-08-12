# navigation

Root navigation of the app.

## Purpose
Wires feature navigation graphs together and defines the top-level `NavHost`.

## Contains
- Root authentication/application composition
- Separate unauthenticated and consumer-main `NavHost` instances
- Top-level routes
- Asynchronous restoration, recovery, unsupported-account and sign-out states
- Authenticated post-login continuation before the consumer graph
- Navigation helpers & deep links

The auth and main graphs never share a back stack. A successful Login, local
session invalidation, or completed Logout replaces the root composition and
therefore removes the opposite graph and its destinations.
The authenticated subtree is keyed by authoritative `userId`: a direct A-to-B
session replacement restarts B's continuation, while a same-user token refresh
keeps the existing navigation lifecycle.

The post-login continuation has one owner. It completes or defers pending
authenticated dietary onboarding, then claims a pending invitation exactly once
before normal navigation. Claim failure keeps the token and exposes retry in the
consumer shell without clearing the authenticated session. Logout or account
transition cancels the old continuation, and every side effect rechecks the
initiating authenticated account.

The activity-owned navigation ViewModel treats `AuthSessionStore.accountKey`
emissions as
hard account boundaries. It clears family/profile/drawer state immediately,
cancels old work, and applies remote results only when the initiating account
and (where applicable) the account-owned active profile are still current.

A USER may temporarily have no profile. The authenticated shell, drawer, Scanner,
History, family creation, and invitation routes remain available. Scanner and
History render their normal page chrome with in-context profile-setup actions;
their ViewModels and repositories still reject or avoid nonpositive IDs. The
drawer exposes the same authenticated setup flow and never fabricates an ID-0
profile. Family creation and invitation remain available because they can create
or attach the caller's SELF profile.

## Does not contain
- Feature screens or ViewModels
- Business logic

Features expose their own navigation APIs; this package only composes them.
