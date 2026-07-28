# CanMakan

CanMakan is an AI-powered barcode ingredient interpreter planned to help users scan packaged food, understand its ingredients, and receive clear, useful dietary information.

## Planned System Components

- Client applications:
  - Mobile application for barcode scanning and user-facing results
  - Web application for browser-based access and administration
- Server components:
  - Backend services for business logic, authentication, and integrations
  - Machine-learning services for ingredient interpretation and classification
  - Agentic AI workflows for contextual explanations and recommendations
- Database layer for application, product, and ingredient data
- Deployment configuration for development and production environments

## Planned Technology Stack

The stack is provisional and will be confirmed during architecture and requirements planning.

- Mobile: Android
- Web: React and Node.js tooling
- Backend: Java with Spring Boot
- Machine learning: Python
- Agentic AI: Python-based AI orchestration and model integrations
- Database: Relational database technology to be selected
- Deployment: Container and cloud tooling to be selected

## Repository Structure

```text
.
|-- client/
|   |-- mobile/             # Future Android mobile application
|   `-- web/                # Future React web application
|-- server/
|   |-- backend/            # Future Spring Boot backend services
|   |-- machine-learning/   # Future ML models, pipelines, and evaluation
|   `-- agentic-ai/         # Future agentic AI workflows and integrations
|-- database/               # Future schemas, migrations, and seed data
|-- deployment/             # Future deployment and infrastructure configuration
|-- docs/
|   |-- architecture/       # Architecture decisions and system designs
|   |-- requirements/       # Functional and non-functional requirements
|   |-- api/                # API specifications and integration notes
|   |-- database/           # Data models and database documentation
|   `-- sprint/             # Sprint plans, reviews, and supporting notes
`-- .github/                # Pull request and issue templates
```

## Current Status

Initial repository setup only. No application, framework, database, deployment, or runtime project has been initialized.
