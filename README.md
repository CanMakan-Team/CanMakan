# CanMakan

CanMakan is an AI-powered barcode ingredient interpreter planned to help users
scan packaged food, understand its ingredients, and receive clear, useful
dietary information.

## Confirmed technology

- Mobile: Android with Jetpack Compose
- Web: React with Vite using JavaScript
- Backend: Spring Boot with Maven and Java 21

The machine-learning, agentic AI, database, and deployment technology choices
remain pending.

## Repository Structure

```text
.
|-- client/
|   |-- mobile/             # Android Jetpack Compose project
|   `-- web/                # React and Vite project
|-- server/
|   |-- backend/            # Spring Boot, Maven, Java 21
|   |-- machine-learning/   # Reserved ML component
|   `-- agentic-ai/         # Reserved Agentic AI and RAG component
|-- database/               # Reserved database area
|-- deployment/             # Reserved deployment and infrastructure area
|-- docs/
|   |-- architecture/       # Architecture decisions and system designs
|   |-- requirements/       # Functional and non-functional requirements
|   |-- api/                # API specifications and integration notes
|   |-- database/           # Data models and database documentation
|   `-- sprint/             # Sprint plans, reviews, and supporting notes
`-- .github/                # Pull request and issue templates
```

## Local start

Android debug build (requires JDK 17 and Android SDK 34):

```powershell
cd client/mobile
.\gradlew.bat :app:assembleDebug
```

Web development server:

```powershell
cd client/web
npm install
npm run dev
```

Backend application (requires Java 21):

```powershell
cd server/backend
.\mvnw.cmd spring-boot:run
```

The backend health endpoint is
`http://localhost:8080/actuator/health`.

## Current status

The mobile directory contains the supplied UI prototype and sample data. The web
and backend directories are runnable initial skeletons only. Barcode camera
integration, backend business APIs, authentication, databases, machine
learning, agentic AI, and deployment configuration are not implemented.
