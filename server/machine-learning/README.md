# UC5 Tier C — Python TF-IDF ranker

## Python vs Java split

| Layer | Location | Role |
|-------|----------|------|
| **Python (online rank)** | `server/machine-learning/src/canmakan_ml/` | Field-weighted TF-IDF fit, cosine rank, domain boosts via FastAPI |
| **Spring (discovery + safety)** | `server/backend/.../recommendation/` | Tag/category discovery, dietary filter, top-5 API, logs |

Spring calls `POST /rank` on the Python service **after** `filterAcceptable`. If the ranker is down or `ranker-url` is empty, Spring falls back to the Java `MlContentBasedRanker` / heuristic ranker.

## Setup

From `server/machine-learning/`:

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # macOS/Linux
pip install -r requirements.txt
```

## Offline pipeline

```bash
python scripts/export_products.py
python scripts/train_ranker.py
python scripts/evaluate.py
python scripts/audit_substitute_tags.py
```

- Input seed: `server/backend/src/main/resources/01_products.sql`
- Intermediate: `artifacts/products.json`
- Ranker artifact: `artifacts/tfidf_ranker.joblib` (not committed; train locally or in CI)
- Legacy vectors: `artifacts/product_feature_vectors.json` (optional, for `evaluate.py` cosine checks)

## Run the rank API

```bash
# after train_ranker.py
set ARTIFACT_PATH=artifacts/tfidf_ranker.joblib
uvicorn canmakan_ml.api:app --app-dir src --host 127.0.0.1 --port 8091
```

Or with Docker (train first so `artifacts/tfidf_ranker.joblib` exists):

```bash
docker build -t canmakan-ml:local .
docker run --rm -p 8091:8091 canmakan-ml:local
```

The image uses `requirements-runtime.txt` (no pytest/pip in the final Alpine image).

CI trains from `01_products.sql`, scans the image with Trivy, and on `develop`/`main` pushes `ghcr.io/<owner>/canmakan-ml:<sha>`. EC2 runs it as `canmakan-ml` on Docker network `canmakan`; Spring uses `http://canmakan-ml:8091`.

Endpoints:

- `GET /health` — liveness
- `POST /rank` — source + SAFE candidates + profile hints → sorted scores

Point Spring at the service:

```properties
canmakan.recommendation.ml.ranker-url=http://127.0.0.1:8091
```

## Tests

```bash
pytest --cov=canmakan_ml --cov-report=xml --cov-fail-under=80
```

CI uploads that Cobertura XML to SonarCloud project `canmakan-ml` (same org as the other stacks). Offline `scripts/` are not in that project’s sources; Semgrep still scans them.

Gold-set barcodes align with `RecommendationServiceIntegrationTest` and `artifacts/labeled_substitute_pairs.json`.

## Substitute tag coverage

See `scripts/audit_substitute_tags.py` — writes `artifacts/tag_coverage.json` and `01c_recommendation_substitute_tags.sql`.

Demo gold-set SQL: `server/backend/src/main/resources/01f_uc5_demo_gold_set.sql`. Re-run `train_ranker.py` after catalog updates.
