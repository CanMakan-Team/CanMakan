# Contributing to CanMakan

## Team Workflow

1. Do not develop directly on `main`.
2. Update your local repository from the latest `main` branch.
3. Create a dedicated feature, documentation, fix, or chore branch.
4. Make clear, small commits that each represent a focused change.
5. Push the branch to the remote repository.
6. Open a pull request into `main`.
7. Obtain team review before merging.

## Branch Naming

Use a concise, descriptive branch name. Examples:

- `feature/mobile-feature-name`
- `feature/web-feature-name`
- `feature/backend-feature-name`
- `feature/ml-feature-name`
- `feature/ai-feature-name`
- `docs/document-name`
- `fix/issue-name`
- `chore/task-name`

## Component checks

Run the checks relevant to the component you changed before requesting review:

```powershell
# Android (from client/mobile)
.\gradlew.bat :app:assembleDebug

# Web (from client/web)
npm run build

# Backend (from server/backend)
.\mvnw.cmd test
```

Do not commit generated output such as `build/`, `dist/`, `target/`,
`node_modules/`, local environment files, or IDE caches.
