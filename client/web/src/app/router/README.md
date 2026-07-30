# app/router

Application routing.

## Purpose
Defines the top-level route configuration and wires feature pages together.

## Contains
- `AppRoutes.tsx` (or equivalent route definitions)
- Route guards / protected route composition (if kept here)

## Does not contain
- Feature page components
- Business logic
- Shared UI

Features own their pages; this folder only composes the routes.