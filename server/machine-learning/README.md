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

Online discovery expands when fewer than 5 SAFE alternatives remain, **inside the
source use-type slice** (curated tags; labels/siblings only when the slice is not
flour/bread/cereal/peanut). Candidates are cosine-ranked with allergen tokens
downweighted on the source vector. The rule engine still drops UNSAFE rows.
Cap is 50 pre-filter candidates; the API still returns at most 5.

Do not run `audit_substitute_tags.py` against breakfast cereals in a way that
appends `Gluten free bread` — the script skips bread tags when the row is a cereal.

Demo gold-set SQL: `server/backend/src/main/resources/01f_uc5_demo_gold_set.sql`.
Rebuild vectors after applying that file so the artifact matches the DB.

Labeled ranking pairs: `artifacts/labeled_substitute_pairs.json` (used by `evaluate.py`).

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
- `MlContentBasedRanker` — cosine + nutrition + LOW_SUGAR + domain boosts from the substitute profile
- `MlSparseCatalogRecommender` — Tier C discovery when Tier A has fewer than 5 SAFE alternatives

`RecommendationService` flow (MVP):

1. Tier A same-category → tag fallback
2. Tier C ML expansion if fewer than 5 SAFE alternatives (when ML enabled) — cosine inside the use-type slice
3. Hybrid ML ranker (cosine + domain boosts); heuristic ranker only when ML is disabled
4. Empty list if still none (LLM is not on the MVP path)

Example: profile 3 (Emily Tan, dairy intolerance + low sugar) scanning
`8888200602857` (Farmhouse Fresh Milk).

Run backend tests:

```bash
cd server/backend
./mvnw test -Dtest=MlContentBasedRankerTest,MlSparseCatalogRecommenderTest,RecommendationServiceTest
```
