"""FastAPI rank service for UC5 alternative recommendations."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from fastapi import FastAPI, HTTPException

from canmakan_ml.domain import ProfileHints
from canmakan_ml.models import RankRequest, RankResponse, RankedProductResponse
from canmakan_ml.ranker import ProductTfidfModel, rank_products

app = FastAPI(title="CanMakan UC5 Ranker", version="0.1.0")


@lru_cache
def load_model() -> ProductTfidfModel:
    artifact_path = os.environ.get("ARTIFACT_PATH", "artifacts/tfidf_ranker.joblib")
    path = Path(artifact_path)
    if not path.is_file():
        raise FileNotFoundError(f"Ranker artifact not found: {path}")
    return ProductTfidfModel.load(path)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rank", response_model=RankResponse)
def rank(request: RankRequest) -> RankResponse:
    try:
        model = load_model()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    profile = ProfileHints(
        prefer_low_sugar=request.profile.prefer_low_sugar,
        milk_substitute_discovery=request.profile.milk_substitute_discovery,
        flour_substitute_discovery=request.profile.flour_substitute_discovery,
        peanut_spread_discovery=request.profile.peanut_spread_discovery,
        secondary_include_tags=request.profile.secondary_include_tags,
        deprioritize_tags=request.profile.deprioritize_tags,
        include_tags=request.profile.include_tags,
        prior_safe_barcodes=set(request.profile.prior_safe_barcodes),
    )
    ranked = rank_products(
        model,
        request.source.to_dict(),
        [candidate.to_dict() for candidate in request.candidates],
        profile,
    )
    return RankResponse(
        ranked=[
            RankedProductResponse(
                barcode=item.barcode,
                score=item.score,
                match_reason=item.match_reason,
            )
            for item in ranked
        ]
    )
