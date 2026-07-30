# navigation

Root navigation of the app.

## Purpose
Wires feature navigation graphs together and defines the top-level `NavHost`.

## Contains
- Root `NavHost` / navigation graph
- Top-level routes
- Start destination logic (auth vs main)
- Navigation helpers & deep links

## Does not contain
- Feature screens or ViewModels
- Business logic

Features expose their own navigation APIs; this package only composes them.