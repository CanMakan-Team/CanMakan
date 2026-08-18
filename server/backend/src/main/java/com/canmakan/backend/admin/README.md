# admin

System administration features.

## Purpose
Supports administrative and operational management of the platform.

## Package layout (large feature)

Nested like other large features: `dto/`, `exception/`, `model/`, `repository/`, `service/`, with controllers at or near the root.

## Responsibilities
- User account management
- Role and access rights management
- System health / actuator information
- Subscription / premium plan management (future)

## Access
All endpoints in this package should be protected by admin-level roles.
