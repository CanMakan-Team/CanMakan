# Design tokens

Shared visual tokens for CanMakan **web** and **mobile**. Colors are the
source of truth here; platform theme files are generated.

## Why tokens (not a shared UI library)

Android uses Jetpack Compose + Material 3. Web uses React + plain CSS.
A single component library would force a large stack change. Tokens keep
brand colors aligned without sharing Compose/React UI code.

## Workflow

1. Edit [`colors.json`](./colors.json) (semantic / brand entries with `compose`
   and/or `css` names).
2. From the repo root, regenerate platform files:

```bash
node design-tokens/generate.mjs
```

Or from `client/web`:

```bash
npm run tokens:generate
```

3. Commit `colors.json` together with the generated outputs:
   - `client/mobile/.../shared/ui/theme/Color.kt`
   - `client/web/src/styles/tokens.css`

Do not hand-edit the generated files.

## Outputs

| File | Role |
| --- | --- |
| `colors.json` | Semantic + brand + web palette colors |
| `generate.mjs` | Zero-dependency Node generator |
| `migrate-web-css.mjs` | Rewrites `#hex` / matching `rgba()` in `app.css` to `var()` / `color-mix` |
| `Color.kt` | Compose named colors (`PrimaryGreen`, …) |
| `tokens.css` | `--color-*` vars + legacy `--teal-*` / `--safe` aliases |

Web `app.css` imports `tokens.css` and should reference `var(--color-…)` (or
legacy aliases), not raw hex. Mobile UI should use Compose vals from `Color.kt`
(for example `CardWhite`, `OnDark`, `TextSecondary`) instead of `Color.White`
or `Color(0x…)`.

## Adding a color

1. Add an entry under `colors` in `colors.json` with `hex`, optional `alpha`,
   `compose` name (or `null` for web-only), and `css` variable.
2. If web CSS still uses an old name, add it under `cssAliases`.
3. Run the generator.
4. If `app.css` still has matching hardcodes, run:

```bash
node design-tokens/migrate-web-css.mjs
```

Web-only one-offs from the portal UI live as `web*` / `--color-web-*` entries.
Prefer promoting repeated values to named semantic tokens over growing the
hex dump when you touch related UI.
