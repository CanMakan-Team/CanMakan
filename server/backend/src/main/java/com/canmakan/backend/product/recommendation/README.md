# product.recommendation

UC5 alternative-product recommendations (catalog query, filter, rank, history).

## Layout

Thin root (HTTP + facade) plus capability folders:

```
recommendation/
  RecommendationController.java
  RecommendationHistoryController.java
  RecommendationService.java          # UC5 facade
  README.md

  catalog/     # CatalogProduct read model, repo, mapper, query
  filter/      # Candidate eligibility + substitute profiles + tag/pack parsers
  ranking/     # Tier A/C rankers, feature vectors, Python TF-IDF client
  discovery/   # Optional LLM / discovery-tier types (not MVP default path)
  history/     # Recommendation history + recommendation logs / AI logs
  dto/         # HTTP request/response records only
```

## Dual products entity

`catalog.CatalogProduct` is the rich UC5 projection of `products`.  
`product.model.ScanProduct` remains the narrow scan-history projection. Do not merge them.

## Notes

- New multi-class collaborators belong in a capability folder, not the slice root.
- Prefer keeping internal helpers package-private when tests live in the same subpackage.
- Catalog alternative scoring uses `DietaryRuleEngine.assessForRecommendation`, which resolves ingredients locally (no Tavily) and caches identical ingredient/label rows within one request.
