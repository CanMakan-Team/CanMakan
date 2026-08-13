# UC5 Tier C — content-based recommendation (Phase 1 MVP)

## Python vs Java split

| Layer | Location | Role |
|-------|----------|------|
| **Python (offline batch)** | `server/machine-learning/` | Export catalog rows, build TF-IDF vectors, write versioned artifacts |
| **Java (online scoring)** | `server/backend/.../recommendation/` | Load catalog at runtime, cosine similarity, nutrition boosts, Tier C discovery |

Python scripts do **not** run in the request path. The Spring Boot backend scores candidates online using
`ProductFeatureEncoder`, `CosineSimilarity`, `MlContentBasedRanker`, and `MlSparseCatalogRecommender`.

Toggle Tier C at runtime:

```properties
canmakan.recommendation.ml.enabled=${CANMAKAN_RECOMMENDATION_ML_ENABLED:true}
```

When `false`, UC5 falls back to Tier A heuristic ranking only.

## Offline pipeline

From `server/machine-learning/`:

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # macOS/Linux
pip install -r requirements.txt

python scripts/export_products.py
python scripts/build_vectors.py
python scripts/evaluate.py
python scripts/audit_substitute_tags.py
```

Defaults:

- Input seed: `server/backend/src/main/resources/01_products.sql`
- Intermediate: `artifacts/products.json`
- Output: `artifacts/product_feature_vectors.json`

## Substitute tag coverage (more alternatives)

`scripts/audit_substitute_tags.py` counts how many catalog rows already have UC5 substitute
tags versus rows that look eligible from `labels_tags` / `labels_en` but are missing the tag.

It writes:

- `artifacts/tag_coverage.json` — coverage counts per substitute tag
- `server/backend/src/main/resources/01c_recommendation_substitute_tags.sql` — additive `UPDATE`
  statements that append tags (does not rewrite `01_products.sql`)

Example mappings: gluten-free labels on a sauce → `Gluten Free sauces`; vegan ice cream →
`ice-creams-and-sorbets`. Re-run the script after catalog refreshes, then restart the backend
so Spring SQL init applies `01c_recommendation_substitute_tags.sql`.

Online discovery also expands when fewer than 5 SAFE alternatives remain: it unions
`category_tags`, `labels_tags` (`en:no-gluten`, `en:vegan`, `en:low-salt`, …), and sibling
`main_category_en` values, then cosine-ranks the merged pool. The rule engine still drops UNSAFE
rows. Cap is 50 pre-filter candidates; the API still returns at most 5.

## `product_feature_vectors.json` format

```json
{
  "version": 1,
  "generated_at": "2026-08-13T04:00:00+00:00",
  "source": "artifacts/products.json",
  "vectorizer": {
    "type": "tfidf",
    "max_features": 5000,
    "fields": ["product_name", "brand", "main_category_en", "category_tags", "labels_tags"]
  },
  "vocabulary_size": 1234,
  "product_count": 5678,
  "products": {
    "8888200602857": {
      "farmhouse": 0.42,
      "fresh": 0.31,
      "milk": 0.55
    }
  }
}
```

Each barcode maps to a sparse `{term: weight}` object (TF-IDF weights, L2-normalizable).

Point the backend at the artifact to prefer precomputed vectors over inline encoding:

```powershell
$env:CANMAKAN_RECOMMENDATION_ML_ARTIFACT_PATH = "..\machine-learning\artifacts\product_feature_vectors.json"
```

## Java components (online)

- `ProductFeatureVectorStore` — loads `product_feature_vectors.json` when configured
- `ProductFeatureEncoder` — precomputed or inline sparse vectors from name, category, tags, allergens
- `CosineSimilarity` — similarity scoring
- `MlContentBasedRanker` — ranks SAFE candidates (nutrition similarity, LOW_SUGAR boost, prior Safe boost)
- `MlSparseCatalogRecommender` — Tier C discovery when Tier A has fewer than 5 SAFE alternatives

`RecommendationService` flow:

1. Tier A same-category → tag fallback
2. Tier C ML expansion if fewer than 5 SAFE alternatives (when ML enabled)
3. Tier B LLM discovery if still empty (when LLM enabled)
4. ML re-rank when expansion ran, source metadata is sparse, or provenance is ML (Tier A/C only; not Tier B)

Example: profile 3 (Emily Tan, dairy intolerance + low sugar) scanning
`8888200602857` (Farmhouse Fresh Milk with placeholder ingredients `Fresh milks`).

Run backend tests:

```bash
cd server/backend
./mvnw test -Dtest=MlContentBasedRankerTest,MlSparseCatalogRecommenderTest,RecommendationServiceTest
```
