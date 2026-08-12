# product/recommendation

Alternative product suggestions.

## Responsibilities
- Show recommended safer alternatives
- Allow user to view details of a recommended product
- Load Recommendation History only for a positive active profile owned by the
  current authenticated account/session

Profile and account changes clear previous entries immediately, and stale
responses are ignored even if cancellation is not respected. Profile-less users
can open Recommendation History in the normal shell, receive a profile-setup
action, and never issue a request with profile id `0`.
