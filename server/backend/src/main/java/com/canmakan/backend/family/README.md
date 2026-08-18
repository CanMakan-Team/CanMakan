# family

Family accounts and member relationships.

## Purpose
Manages family grouping, member profiles, invitations, and active profile selection.

## Package layout (large feature)

Thin root + nested collaborators (F19 / P2):

```
family/
  FamilyController.java          # HTTP
  InvitationController.java
  FamilyService.java             # Facade over service/*
  FamilyRelationshipToAdmin.java
  README.md
  config/                        # InviteProperties, ResendProperties
  service/                       # Roster, invitation, authz, email, notifier, util
  dto/
  model/
  repository/
  exception/
```

Do not put new multi-class collaborators at the package root; prefer `service/` (or `config/` for properties).

API contract: `docs/api/families.md`

## Related packages
- Uses `dietaryprofile` for SELF bootstrap on create and profile restrictions
- Uses `user` for accounts and preferences
- Uses `notification` for invite inbox cards
