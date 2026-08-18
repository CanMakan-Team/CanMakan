# user

Platform user accounts and account-level preferences.

## Purpose
Owns the `users` / `user_preferences` persistence used by auth, family, admin, and notifications.

## Package layout (aligned with dietaryprofile)

```
user/
  UserPreferenceController.java
  UserPreferenceService.java
  AuthenticationAccountView.java   # Projection for auth lookups
  AdminUserSummaryView.java        # Projection for admin listing
  model/                           # UserAccount, UserPreference
  repository/                      # Matching Spring Data repos
  dto/                             # Notification preference request/response
```

Controllers and application services stay at the root; JPA types live under `model/` / `repository/`.
