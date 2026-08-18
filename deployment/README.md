# Deployment

Staging and production **backend** and **UC5 ranker** runtimes are Docker images published to GitHub Container Registry and run on the same EC2 host.

| Piece | Location |
| --- | --- |
| Backend image | [`server/backend/Dockerfile`](../server/backend/Dockerfile) |
| Ranker image | [`server/machine-learning/Dockerfile`](../server/machine-learning/Dockerfile) |
| CI (build, Trivy image scan, `ghcr.io` push) | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) jobs `build-backend` and `build-machine-learning` |
| CD (pull + container swap) | [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) and [`.github/scripts/deploy-backend-container.sh`](../.github/scripts/deploy-backend-container.sh) |

Images:

- `ghcr.io/<lowercase-owner>/canmakan-backend:<git-sha>` (also tagged `develop` / `main`)
- `ghcr.io/<lowercase-owner>/canmakan-ml:<git-sha>` (also tagged `develop` / `main`)

The host still runs Nginx as the TLS reverse proxy. Backend blue/green maps `127.0.0.1:8080` and `127.0.0.1:8081`. The ranker is `canmakan-ml` on `127.0.0.1:8091` (not exposed through Nginx). Both containers join Docker network `canmakan` so Spring can call `http://canmakan-ml:8091`.

Operator steps (GHCR package visibility, Environment secrets, first ML merge) are in [CICD-PIPELINE.md](../CICD-PIPELINE.md) section **7.1**.

Web and mobile continue to deploy through Firebase (`deploy-frontends.yml`). This folder does not hold Terraform or RDS schema migrations (see CICD-PIPELINE.md gaps 2–3).
