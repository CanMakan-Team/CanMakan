# CanMakan Web

This directory contains the initial JavaScript web-client skeleton for CanMakan,
built with React and Vite. It currently provides only a placeholder for the
planned Family Admin Portal and System Admin Portal. Authentication, dashboards,
business features, API requests, and production UI are not implemented.

## Install

```powershell
npm install
```

## Develop

```powershell
npm run dev
```

## Production build

```powershell
npm run build
```

## Environment

Copy `.env.example` to `.env.local` for machine-specific development settings
and adjust values locally if needed:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Vite exposes client-side variables whose names begin with `VITE_`. Do not put
credentials or secrets in these variables or commit local environment files.
`src/services/apiConfig.js` reads the API base URL for future integrations; it
does not make API requests.
