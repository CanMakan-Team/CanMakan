# System overview

CanMakan is one product. The software architecture is grouped by stack: mobile, web, data visualisation, Spring Boot, in-process agentic AI, machine learning, database, and cloud. Android and the React SPA call one Spring Boot API. Staging and production run that API and the Python ranker as Docker containers on EC2 (network `canmakan`), with MySQL on RDS. The SPA is Firebase Hosting; Android QA builds are Firebase App Distribution.

Same graph as the [root README](../../README.md). Scan/assess sequence: [MCP Agent Architecture](mcp-agent-architecture.md). UC5 ranking: [UC5 Alternative Recommender](uc5-alternative-recommender.md). Persistence tables: [Data model](data-model.md). Deploy jobs: [CICD-PIPELINE.md](../devsecops/CICD-PIPELINE.md).

Local development uses MySQL on `localhost:3306` instead of RDS, and skips Nginx / Firebase.

```mermaid
flowchart TB
  subgraph mobile [Mobile]
    Android["Android app<br/>Compose + Retrofit + ML Kit barcode"]
  end

  subgraph web [Web]
    SPA["React + Vite SPA<br/>USER / family admin / system admin"]
  end

  subgraph viz [Data visualisation]
    Charts["Custom SVG + CSV<br/>UC7 UC14 UC15 UC22"]
  end

  subgraph spring [Spring Boot]
    Security["auth + JWT"]
    Product["product<br/>scan · assess · recommend"]
    Household["family + dietaryprofile"]
    Admin["admin + analytics"]
    Engine["DietaryRuleEngine"]
    Integration["integration<br/>OFF + EAN-Search"]
  end

  subgraph agentic [Agentic AI]
    MCP["in-process MCP dietary tools"]
    Llm["LlmClient evidence"]
  end

  subgraph ml [Machine learning]
    Ranker["Python FastAPI<br/>TF-IDF ranker :8091"]
  end

  subgraph database [Database]
    RDS[(RDS MySQL)]
  end

  subgraph cloud [Cloud]
    GHA[GitHub Actions]
    GHCR["GHCR images"]
    EC2["EC2 + Docker network canmakan + Nginx TLS"]
    Hosting[Firebase Hosting]
    AppDist[Firebase App Distribution]
  end

  subgraph ext [External APIs]
    OFF[Open Food Facts]
    EAN[EAN-Search]
    OpenAI[OpenAI]
    Tavily[Tavily]
    Resend[Resend]
  end

  AppDist --> Android
  Hosting --> SPA
  Android -->|"HTTPS JSON + JWT /api"| EC2
  SPA -->|"HTTPS JSON + JWT /api"| EC2
  EC2 --> Security
  SPA --> Charts
  Charts --> Admin
  SPA --> Household
  SPA --> Admin
  Security --> Product
  Security --> Household
  Security --> Admin
  Product --> Engine
  Engine --> MCP
  MCP --> Llm
  Product --> Integration
  Product -->|"HTTP POST /rank"| Ranker
  Integration -->|"validate then assess"| OFF
  Integration -->|"validate fallback"| EAN
  Llm -.->|"CANMAKAN_AI_ENABLED"| OpenAI
  MCP -.->|"allergen search"| Tavily
  Household -.->|"invite email"| Resend
  spring -->|"JDBC"| RDS
  spring -->|"Docker"| EC2
  Ranker -->|"Docker"| EC2
  SPA -->|"Hosting"| Hosting
  GHA --> GHCR
  GHCR -->|"docker pull"| EC2
  GHA --> Hosting
  GHA --> AppDist
```

| Stack | What it is |
| --- | --- |
| Mobile | Consumer app; does not compute verdicts |
| Web | Family-admin and system-admin SPA |
| Data visualisation | Custom SVG/CSV **in the web SPA**; JSON from `admin`/`analytics` |
| Spring Boot | JWT API, rule engine, family/scan, OFF + EAN clients |
| Agentic AI | In-process MCP tools + `LlmClient`; not a separate container ([`server/agentic-ai/`](../../server/agentic-ai/README.md) is empty) |
| Machine learning | Python TF-IDF ranker after Java safety filter |
| Database | RDS MySQL (local: MySQL 8) |
| Cloud | GitHub Actions, GHCR, EC2/Docker/Nginx, Firebase Hosting and App Distribution |
| External APIs | OFF (validate + assess), EAN-Search (validate fallback), optional OpenAI / Tavily / Resend |

Validate uses Open Food Facts then **EAN-Search**. Assess uses OFF only (optional Tavily for unresolved allergen labels). UC5 catalog scoring does not call Tavily. Ranker port 8091 is Docker-internal (`http://canmakan-ml:8091`); Nginx does not expose it.
