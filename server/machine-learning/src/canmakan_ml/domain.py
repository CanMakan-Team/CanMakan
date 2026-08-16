"""Domain-aware score adjustments mirroring Java MlContentBasedRanker."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

from canmakan_ml.features import is_unsweetened, normalize_tags

CONTENT_SIM_SCALE = 0.63
LOW_SUGAR_BOOST = 0.12
PRIOR_SAFE_BOOST = 0.10
NUTRITION_WEIGHT = 0.15
MAX_SUGAR_RANGE_G = 50.0
MAX_SODIUM_RANGE_G = 3.0
MAX_SCORE = 0.99
DOMAIN_BOOST = 0.03
FLOUR_TYPE_BOOST = 0.05
NUT_BUTTER_EXTRA_BOOST = 0.02
COOKING_PENALTY = 0.10
PACK_SIZE_WEIGHT = 0.08
MAX_PACK_RANGE_ML = 1000.0
MIN_PACK_MATCH_SIMILARITY = 0.85

MILLILITRE_PATTERN = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*(ml|millilitre?s?|milliliter?s?)",
    re.IGNORECASE,
)
LITRE_PATTERN = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*(l|litre?s?|liter?s?)(?![a-z])",
    re.IGNORECASE,
)
CENTILITRE_PATTERN = re.compile(r"(\d+(?:[.,]\d+)?)\s*(cl)", re.IGNORECASE)


@dataclass
class ProfileHints:
    prefer_low_sugar: bool = False
    milk_substitute_discovery: bool = False
    flour_substitute_discovery: bool = False
    peanut_spread_discovery: bool = False
    secondary_include_tags: list[str] = field(default_factory=list)
    deprioritize_tags: list[str] = field(default_factory=list)
    include_tags: list[str] = field(default_factory=list)
    prior_safe_barcodes: set[str] = field(default_factory=set)


def _parse_amount(raw: str) -> float:
    return float(raw.replace(",", "."))


def parse_volume_ml(raw: str | None) -> float | None:
    if not raw or not str(raw).strip():
        return None
    normalized = str(raw).strip().lower().replace(",", ".")
    match = MILLILITRE_PATTERN.search(normalized)
    if match:
        return _parse_amount(match.group(1))
    match = LITRE_PATTERN.search(normalized)
    if match:
        return _parse_amount(match.group(1)) * 1000.0
    match = CENTILITRE_PATTERN.search(normalized)
    if match:
        return _parse_amount(match.group(1)) * 10.0
    return None


def resolve_volume_ml(product: dict[str, Any]) -> float | None:
    for key in ("quantity", "serving_size"):
        volume = parse_volume_ml(product.get(key))
        if volume is not None:
            return volume
    serving_quantity = product.get("serving_quantity")
    if serving_quantity is not None:
        try:
            value = float(serving_quantity)
        except (TypeError, ValueError):
            return None
        if 0 < value <= 5000:
            return value
    return None


def _nutrition_value(product: dict[str, Any], key: str) -> float | None:
    value = product.get(key)
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def has_nutrition_pair(product: dict[str, Any]) -> bool:
    return (
        _nutrition_value(product, "sugars_100g") is not None
        and _nutrition_value(product, "sodium_100g") is not None
    )


def nutrition_similarity(source: dict[str, Any], candidate: dict[str, Any]) -> float:
    sugar_delta = abs(
        _nutrition_value(source, "sugars_100g") - _nutrition_value(candidate, "sugars_100g")
    )
    sodium_delta = abs(
        _nutrition_value(source, "sodium_100g") - _nutrition_value(candidate, "sodium_100g")
    )
    sugar_sim = 1.0 - min(1.0, sugar_delta / MAX_SUGAR_RANGE_G)
    sodium_sim = 1.0 - min(1.0, sodium_delta / MAX_SODIUM_RANGE_G)
    return ((sugar_sim + sodium_sim) / 2.0) * NUTRITION_WEIGHT


def pack_size_similarity(source: dict[str, Any], candidate: dict[str, Any]) -> float:
    source_ml = resolve_volume_ml(source)
    candidate_ml = resolve_volume_ml(candidate)
    if source_ml is None or candidate_ml is None:
        return 0.0
    delta_ml = abs(source_ml - candidate_ml)
    return 1.0 - min(1.0, delta_ml / MAX_PACK_RANGE_ML)


def _normalized_tag_set(raw: str | None, main_category: str | None = None) -> set[str]:
    tags = set(normalize_tags(raw).split())
    if main_category:
        tags.add(main_category.strip().lower().replace(" ", "-"))
    return tags


def _contains_any(tags: set[str], needles: list[str]) -> bool:
    if not needles:
        return False
    normalized = {n.strip().lower().replace(" ", "-").removeprefix("en:") for n in needles}
    normalized |= {f"en:{n}" for n in normalized}
    return bool(tags & normalized) or any(
        any(needle in tag for tag in tags) for needle in normalized
    )


def domain_adjustment(candidate: dict[str, Any], profile: ProfileHints) -> float:
    tags = _normalized_tag_set(candidate.get("category_tags"), candidate.get("main_category_en"))
    adjustment = 0.0
    if _contains_any(tags, profile.secondary_include_tags):
        adjustment += DOMAIN_BOOST
        if profile.flour_substitute_discovery:
            adjustment += FLOUR_TYPE_BOOST
    if (
        profile.peanut_spread_discovery
        and _contains_any(tags, ["en:nut-butters", "nut-butters"])
        and _contains_any(set(profile.include_tags), ["en:nut-butters", "nut-butters"])
    ):
        adjustment += NUT_BUTTER_EXTRA_BOOST
    if _contains_any(tags, profile.deprioritize_tags):
        adjustment -= COOKING_PENALTY
    return adjustment


def resolve_match_reason(
    prior_safe: bool,
    nutrition_matched: bool,
    unsweetened_matched: bool,
    pack_size_matched: bool,
) -> str:
    if prior_safe:
        return "ml_prior_safe_scan"
    if nutrition_matched:
        return "ml_nutrition_match"
    if unsweetened_matched:
        return "ml_unsweetened_substitute"
    if pack_size_matched:
        return "ml_pack_size_match"
    return "ml_similarity"


def score_candidate(
    content_similarity: float,
    source: dict[str, Any],
    candidate: dict[str, Any],
    profile: ProfileHints,
) -> tuple[float, str]:
    score = content_similarity * CONTENT_SIM_SCALE
    nutrition_available = has_nutrition_pair(source)
    nutrition_matched = nutrition_available and has_nutrition_pair(candidate)
    unsweetened_matched = profile.prefer_low_sugar and is_unsweetened(candidate)
    pack_size_available = profile.milk_substitute_discovery and resolve_volume_ml(source) is not None
    pack_size_matched = (
        pack_size_available
        and resolve_volume_ml(candidate) is not None
        and pack_size_similarity(source, candidate) >= MIN_PACK_MATCH_SIMILARITY
    )

    if nutrition_matched and profile.prefer_low_sugar:
        score += nutrition_similarity(source, candidate)
    elif unsweetened_matched:
        score += LOW_SUGAR_BOOST
    if pack_size_available and resolve_volume_ml(candidate) is not None:
        score += pack_size_similarity(source, candidate) * PACK_SIZE_WEIGHT
    score += domain_adjustment(candidate, profile)

    prior_safe = candidate.get("barcode") in profile.prior_safe_barcodes
    if prior_safe:
        score += PRIOR_SAFE_BOOST

    score = min(score, MAX_SCORE)
    reason = resolve_match_reason(prior_safe, nutrition_matched and profile.prefer_low_sugar, unsweetened_matched, pack_size_matched)
    return score, reason
