# session

Lightweight authenticated session tracking.

## Package layout (small feature)

Flat by design (under the ~8–10 type threshold):

```
session/
  SessionController.java
  SessionTrackingService.java
  UserSession.java
  UserSessionRepository.java
```

Add `exception/` and/or `dto/` only when those type kinds appear. Do not nest solely for symmetry with larger features.
