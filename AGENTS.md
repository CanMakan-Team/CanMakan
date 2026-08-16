# Instructions for Coding Agents

Before making changes:

- Read the root `README.md` and the documentation relevant to the task.
- Keep client components under `client/` and server components under `server/`.
- Keep `client/mobile`, `client/web`, `server/backend`, `server/machine-learning`, and `server/agentic-ai` separated.
- Keep Android and Jetpack Compose work under `client/mobile`; Android currently targets JVM 21.
- Keep React and Vite web work under `client/web`.
- Keep Spring Boot and Maven work under `server/backend`; Java 21 applies to this backend.
- Do not overwrite the existing supplied mobile UI implementation without team approval.
- Do not introduce new frameworks without documenting the reason.
- Keep brand colors in `design-tokens/colors.json` and regenerate platform
  theme files with `node design-tokens/generate.mjs` (do not hand-edit
  generated `Color.kt` or `tokens.css`).
- Keep shared mascot PNGs in `client/shared/assets/mascot/` (Android extra
  `res` directory + Vite public mapping). Do not copy them into
  `client/web/public` or `client/mobile/app/src/main/res/drawable`.
- Never add credentials, passwords, secrets, or API keys.
- Update documentation whenever architecture or APIs change.
- Run the relevant tests before completing future coding tasks.
- Avoid modifying unrelated components.
