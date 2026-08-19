# user

Platform user accounts and account-level preferences.

## Purpose
Owns the `users` / `user_preferences` persistence used by auth, family, admin, and notifications.

## Package layout (aligned with dietaryprofile)

```
user/
  UserPreferenceController.java
  README.md
  service/                         # UserPreferenceService
  model/                           # UserAccount, UserPreference
  repository/                      # Repos + AdminUserSummaryView + AuthenticationAccountView
  dto/                             # Notification preference request/response
```

Controllers stay at the root; application services, JPA types, and query projections are nested.
