# common/security

Security infrastructure (not the login feature).

## Purpose
Implements authentication/authorization mechanisms used by the whole application.

## Typical contents
- JWT filter / token provider
- `SecurityFilterChain` configuration
- Role hierarchy and permission constants
- Method security expressions
- Current user extraction utilities

## Note
The actual **login / logout / session** endpoints and flow live in the `auth` package.
This package only provides the underlying security machinery.