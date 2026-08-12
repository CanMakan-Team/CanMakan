# product/recommendation

Alternative product suggestions.

## Responsibilities
- Provide recommendation-history data used by Scan History to attach safer
  alternatives when opening a past scan verdict
- Load recommendation history only for a positive active profile owned by the
  current authenticated account/session

There is no standalone Recommendation History screen on this branch; alternatives
surface through History → product detail. Profile and account changes clear
previous entries immediately, and stale responses are ignored even if
cancellation is not respected.
