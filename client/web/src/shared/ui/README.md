# shared/ui

Shared UI components and layout.

## Purpose
Reusable visual building blocks used across features.

## Contains
- Design-system primitives (buttons, badges, modals, page states, etc.)
- App shell layout (`PortalLayout`)
- Cross-portal `CredentialLoginForm` (used by family and system login pages)
- `PasswordField` (password input with show/hide eye toggle)
- Other cross-feature UI components

## Does not contain
- Feature-specific pages or business UI
- API or domain logic

## Notes
Feature screens live in `features/`.  
This folder is only for shared presentation pieces.