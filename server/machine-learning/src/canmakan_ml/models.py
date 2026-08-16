"""HTTP request/response models for the rank API."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ProductPayload(BaseModel):
    barcode: str
    product_name: str | None = None
    brand: str | None = None
    main_category_en: str | None = None
    category_tags: str | None = None
    labels_tags: str | None = None
    ingredients_text: str | None = None
    quantity: str | None = None
    serving_size: str | None = None
    serving_quantity: float | None = None
    sugars_100g: float | None = None
    sodium_100g: float | None = None

    def to_dict(self) -> dict[str, Any]:
        return self.model_dump(exclude_none=False)


class ProfileHintsPayload(BaseModel):
    prefer_low_sugar: bool = False
    milk_substitute_discovery: bool = False
    flour_substitute_discovery: bool = False
    peanut_spread_discovery: bool = False
    secondary_include_tags: list[str] = Field(default_factory=list)
    deprioritize_tags: list[str] = Field(default_factory=list)
    include_tags: list[str] = Field(default_factory=list)
    prior_safe_barcodes: list[str] = Field(default_factory=list)


class RankRequest(BaseModel):
    source: ProductPayload
    candidates: list[ProductPayload]
    profile: ProfileHintsPayload = Field(default_factory=ProfileHintsPayload)


class RankedProductResponse(BaseModel):
    barcode: str
    score: float
    match_reason: str


class RankResponse(BaseModel):
    ranked: list[RankedProductResponse]
