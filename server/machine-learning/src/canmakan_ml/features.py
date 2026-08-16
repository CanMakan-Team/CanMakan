"""Field-weighted TF-IDF feature builders for catalog products."""

from __future__ import annotations

import re
from typing import Any

ALLERGEN_QUERY_TOKENS = frozenset(
    {
        "peanut",
        "peanuts",
        "groundnut",
        "milk",
        "dairy",
        "wheat",
        "gluten",
        "barley",
        "rye",
        "peanut-butters",
        "en:peanuts",
        "en:milk",
        "en:wheat",
        "en:gluten",
    }
)
ALLERGEN_QUERY_SCALE = 0.15


def normalize_tags(raw: str | None) -> str:
    if not raw:
        return ""
    tags: list[str] = []
    for tag in raw.split(","):
        cleaned = tag.strip().lower().replace(" ", "-")
        if cleaned.startswith("en:"):
            cleaned = cleaned[3:]
        if cleaned:
            tags.append(cleaned)
    return " ".join(tags)


def tokenize_like_java(text: str | None) -> str:
    if not text:
        return ""
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def is_placeholder_ingredients(product: dict[str, Any]) -> bool:
    ingredients = product.get("ingredients_text")
    if not ingredients or not str(ingredients).strip():
        return True
    category = product.get("main_category_en")
    if category is None:
        return False
    return str(ingredients).strip().lower() == str(category).strip().lower()


def field_text(product: dict[str, Any], field: str) -> str:
    value = product.get(field)
    if value is None:
        return ""
    if field in {"category_tags", "labels_tags"}:
        return normalize_tags(str(value))
    return tokenize_like_java(str(value))


def product_matrix_inputs(product: dict[str, Any]) -> list[str]:
    name = field_text(product, "product_name")
    return [
        name,
        name,
        field_text(product, "main_category_en"),
        field_text(product, "brand"),
        field_text(product, "category_tags"),
        field_text(product, "labels_tags"),
    ]


def apply_query_downweight(vector, feature_names: list[str]):
    if vector is None:
        return vector
    row = vector.tocsr()
    for index, name in enumerate(feature_names):
        token = name.lower().replace(" ", "-")
        if token in ALLERGEN_QUERY_TOKENS or name.lower() in ALLERGEN_QUERY_TOKENS:
            row[0, index] = row[0, index] * ALLERGEN_QUERY_SCALE
    return row


def is_unsweetened(product: dict[str, Any]) -> bool:
    name = (product.get("product_name") or "").lower()
    if any(phrase in name for phrase in ("unsweetened", "no sugar", "zero sugar")):
        return True
    labels = normalize_tags(product.get("labels_tags"))
    return any(
        tag in labels.split()
        for tag in ("no-sugar", "low-sugar", "sugar-free", "unsweetened")
    )
