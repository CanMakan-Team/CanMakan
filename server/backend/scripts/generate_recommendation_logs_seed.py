#!/usr/bin/env python3
"""
Generate 10_recommendation_logs.sql from scan seed + products catalog.

Tier A rules mirrored offline:
  - Only WARNING/UNSAFE scans get recommendations
  - Candidates must share main_category_en with the scanned product
  - When same-category SAFE pool is empty, fall back to category_tags substitute profiles
  - Candidates must have non-empty ingredients_text
  - Simple keyword filter approximates DietaryRuleEngine STRICT_AVOID / INTOLERANCE
  - prior_safe_scan boost when profile has a SAFE scan in the same category

Usage:
  python generate_recommendation_logs_seed.py
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RESOURCES = SCRIPT_DIR.parent / "src/main/resources"
PRODUCTS_SQL = RESOURCES / "01_products.sql"
SCANS_SQL = RESOURCES / "06_scans_and_ai_logs.sql"
HOUSEHOLD_SQL = RESOURCES / "05_household_dietary_data.sql"
OUTPUT_SQL = RESOURCES / "10_recommendation_logs.sql"

IDX_BARCODE = 0
IDX_PRODUCT_NAME = 1
IDX_BRAND = 3
IDX_CATEGORY_TAGS = 8
IDX_MAIN_CATEGORY_EN = 10
IDX_INGREDIENTS_TEXT = 13
IDX_ALLERGENS = 15
IDX_LABELS_TAGS = 19
IDX_COMPLETENESS = 25

MAX_ALTERNATIVES_PER_SCAN = 2

SUBSTITUTE_PROFILES: dict[str, dict[str, list[str]]] = {
    "Fresh milks": {
        "include": ["en:milk-substitutes", "en:dairy-substitutes"],
        "beverage": [
            "en:oat-based-drinks",
            "en:soy-based-drinks",
            "en:almond-based-drinks",
            "en:unsweetened-plain-soy-based-drinks",
        ],
        "deprioritize": ["en:plant-based-creams-for-cooking", "en:coconut-milks-and-creams"],
    }
}

# profile_id -> list of (restriction_code, severity)
PROFILE_RULES: dict[int, list[tuple[str, str]]] = {
    1: [("GLUTEN", "STRICT_AVOID"), ("LOW_SUGAR", "PREFERENCE")],
    2: [("LOW_FAT", "PREFERENCE"), ("LOW_SODIUM", "PREFERENCE")],
    3: [("DAIRY", "INTOLERANCE"), ("PEANUT", "STRICT_AVOID"), ("LOW_SUGAR", "PREFERENCE")],
    4: [("HALAL", "STRICT_AVOID"), ("LOW_TRANS_FAT", "PREFERENCE")],
    5: [("HALAL", "STRICT_AVOID"), ("LOW_FAT", "PREFERENCE"), ("LOW_SODIUM", "PREFERENCE")],
    6: [("SHELLFISH", "STRICT_AVOID"), ("HALAL", "STRICT_AVOID")],
    7: [("HALAL", "STRICT_AVOID"), ("LOW_SUGAR", "PREFERENCE")],
    8: [("EGG", "STRICT_AVOID"), ("VEGETARIAN", "STRICT_AVOID"), ("LOW_SODIUM", "PREFERENCE")],
    9: [("VEGAN", "STRICT_AVOID"), ("LOW_TRANS_FAT", "PREFERENCE")],
    10: [("DAIRY", "INTOLERANCE"), ("LOW_SUGAR", "PREFERENCE")],
}

AVOID_KEYWORDS: dict[str, list[str]] = {
    "GLUTEN": ["wheat", "barley", "rye", "gluten", "malt extract", "malted barley", " malt", "orge"],
    "DAIRY": ["milk", "whey", "lactose", "butter", "cheese", "cream", "dairy", "yogurt"],
    "PEANUT": ["peanut"],
    "SHELLFISH": ["shrimp", "crab", "lobster", "oyster", "shellfish", "prawn", "crustacean"],
    "FISH": ["fish", "sardine", "anchovy", "tuna", "salmon"],
    "EGG": ["egg"],
    "HALAL": ["pork", "lard", "mirin", "wine", "beer", "gelatin", "bacon", "ham"],
    "VEGETARIAN": ["chicken", "beef", "pork", "fish", "meat", "shrimp", "crab", "gelatin"],
    "VEGAN": ["milk", "whey", "honey", "egg", "gelatin", "fish", "chicken", "beef", "pork"],
}

POSITIVE_LABELS: dict[str, list[str]] = {
    "GLUTEN": ["no-gluten", "gluten-free"],
    "HALAL": ["halal"],
    "VEGAN": ["vegan"],
}

ALLERGEN_TAGS: dict[str, list[str]] = {
    "GLUTEN": ["en:gluten", "gluten"],
    "DAIRY": ["en:milk", "milk"],
    "PEANUT": ["en:peanuts", "peanut"],
    "SHELLFISH": ["en:crustaceans", "en:molluscs", "shellfish"],
    "EGG": ["en:eggs", "egg"],
}


def allergens_lower(product: Product) -> str:
    return (product.allergens or "").lower()


@dataclass
class Product:
    barcode: str
    name: str
    brand: str | None
    category_en: str | None
    category_tags: str | None
    ingredients_text: str | None
    allergens: str | None
    labels_tags: str | None
    completeness: float


@dataclass
class Scan:
    scan_id: int
    profile_id: int
    barcode: str
    verdict: str
    days_ago: int


def parse_sql_tuple_values(tuple_body: str) -> list[str | None]:
    values: list[str | None] = []
    index = 0
    length = len(tuple_body)

    while index < length:
        while index < length and tuple_body[index] in " \t\r\n":
            index += 1
        if index >= length:
            break

        if tuple_body.startswith("NULL", index):
            values.append(None)
            index += 4
        elif tuple_body[index] == "'":
            index += 1
            chars: list[str] = []
            while index < length:
                char = tuple_body[index]
                if char == "'":
                    if index + 1 < length and tuple_body[index + 1] == "'":
                        chars.append("'")
                        index += 2
                        continue
                    index += 1
                    break
                chars.append(char)
                index += 1
            values.append("".join(chars))
        else:
            start = index
            while index < length and tuple_body[index] != ",":
                index += 1
            token = tuple_body[start:index].strip()
            values.append(token if token else None)

        while index < length and tuple_body[index] in " \t\r\n,":
            index += 1

    return values


def extract_tuple_body(line: str) -> str | None:
    stripped = line.strip()
    if not stripped.startswith("('"):
        return None
    if stripped.endswith("),"):
        return stripped[1:-2]
    if stripped.endswith(");"):
        return stripped[1:-2]
    return None


def parse_products(path: Path) -> dict[str, Product]:
    by_barcode: dict[str, Product] = {}
    pattern = re.compile(r"^\('([^']+)'")

    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not pattern.match(line.strip()):
                continue
            body = extract_tuple_body(line)
            if body is None:
                continue
            try:
                values = parse_sql_tuple_values(body)
            except ValueError:
                continue
            if len(values) <= IDX_INGREDIENTS_TEXT:
                continue

            barcode = (values[IDX_BARCODE] or "").strip()
            if not barcode:
                continue

            completeness_raw = values[IDX_COMPLETENESS] if len(values) > IDX_COMPLETENESS else None
            try:
                completeness = float(completeness_raw) if completeness_raw not in (None, "NULL") else 0.0
            except ValueError:
                completeness = 0.0

            candidate = Product(
                barcode=barcode,
                name=(values[IDX_PRODUCT_NAME] or "").strip(),
                brand=values[IDX_BRAND],
                category_en=(values[IDX_MAIN_CATEGORY_EN] or "").strip() or None,
                category_tags=values[IDX_CATEGORY_TAGS] if len(values) > IDX_CATEGORY_TAGS else None,
                ingredients_text=(values[IDX_INGREDIENTS_TEXT] or "").strip() or None,
                allergens=values[IDX_ALLERGENS] if len(values) > IDX_ALLERGENS else None,
                labels_tags=values[IDX_LABELS_TAGS] if len(values) > IDX_LABELS_TAGS else None,
                completeness=completeness,
            )

            existing = by_barcode.get(barcode)
            if existing is None:
                by_barcode[barcode] = candidate
                continue

            # Prefer row with richer ingredient text, then completeness.
            existing_len = len(existing.ingredients_text or "")
            candidate_len = len(candidate.ingredients_text or "")
            if candidate_len > existing_len or (
                candidate_len == existing_len and candidate.completeness > existing.completeness
            ):
                by_barcode[barcode] = candidate

    return by_barcode


def parse_scans(path: Path) -> list[Scan]:
    scans: list[Scan] = []
    row_pattern = re.compile(
        r"\((\d+),\s*(\d+),\s*(\d+),\s*'([^']+)',\s*'(SAFE|WARNING|UNSAFE)'",
    )
    days_pattern = re.compile(r"NOW\(\)\s*-\s*INTERVAL\s+(\d+)\s+DAY")

    with path.open(encoding="utf-8") as handle:
        for line in handle:
            match = row_pattern.search(line)
            if not match:
                continue
            days_match = days_pattern.search(line)
            days_ago = int(days_match.group(1)) if days_match else 0
            scans.append(
                Scan(
                    scan_id=int(match.group(1)),
                    profile_id=int(match.group(3)),
                    barcode=match.group(4),
                    verdict=match.group(5),
                    days_ago=days_ago,
                )
            )
    return scans


def labels_lower(product: Product) -> str:
    return (product.labels_tags or "").lower()


def category_tags_set(product: Product) -> set[str]:
    if not product.category_tags:
        return set()
    return {tag.strip() for tag in product.category_tags.split(",") if tag.strip()}


def tags_contain_any(tags: set[str], needles: list[str]) -> bool:
    return any(needle in tags for needle in needles)


def substitute_profile_for(category_en: str | None) -> dict[str, list[str]] | None:
    if not category_en:
        return None
    return SUBSTITUTE_PROFILES.get(category_en)


def substitute_candidates(
    products: dict[str, Product],
    source: Product,
    profile_id: int,
) -> list[Product]:
    profile = substitute_profile_for(source.category_en)
    if profile is None:
        return []

    include_tags = profile["include"]
    pool: list[Product] = []
    seen: set[str] = set()
    for product in products.values():
        if product.barcode == source.barcode or not eligible_candidate(product):
            continue
        tags = category_tags_set(product)
        if not tags_contain_any(tags, include_tags):
            continue
        if category_blocked(product, profile_id):
            continue
        if violates_strict_rules(product, profile_id):
            continue
        if product.barcode in seen:
            continue
        seen.add(product.barcode)
        pool.append(product)
    return pool


def ingredients_lower(product: Product) -> str:
    return (product.ingredients_text or "").lower()


def violates_strict_rules(product: Product, profile_id: int) -> bool:
    rules = PROFILE_RULES.get(profile_id, [])
    text = ingredients_lower(product)
    name = (product.name or "").lower()
    tags = labels_lower(product)
    allergen_field = allergens_lower(product)

    for code, severity in rules:
        if severity not in ("STRICT_AVOID", "INTOLERANCE"):
            continue
        positives = POSITIVE_LABELS.get(code, [])
        if any(pos in tags for pos in positives):
            continue
        for tag in ALLERGEN_TAGS.get(code, []):
            if tag in allergen_field:
                return True
        for keyword in AVOID_KEYWORDS.get(code, []):
            if keyword in text or keyword in name:
                return True
    return False


def label_boost(product: Product, profile_id: int) -> int:
    """Higher is better — prefer products with cert/label tags matching profile needs."""
    tags = labels_lower(product)
    score = 0
    for code, severity in PROFILE_RULES.get(profile_id, []):
        if severity not in ("STRICT_AVOID", "INTOLERANCE"):
            continue
        for pos in POSITIVE_LABELS.get(code, []):
            if pos in tags:
                score += 10
    return score


def category_blocked(product: Product, profile_id: int) -> bool:
    """Category-level blocks for profiles where the whole category is usually incompatible."""
    if product.category_en == "Beers":
        for code, severity in PROFILE_RULES.get(profile_id, []):
            if code == "GLUTEN" and severity == "STRICT_AVOID":
                return True
    return False


def eligible_candidate(product: Product) -> bool:
    return bool(product.category_en and product.ingredients_text)


def rank_score(
    candidate: Product,
    position: int,
    prior_safe: bool,
    substitute_profile: dict[str, list[str]] | None = None,
) -> tuple[float, str]:
    if substitute_profile is None:
        base = max(0.5, 1.0 - (position * 0.02))
        if prior_safe:
            return (min(base + 0.10, 0.99), "prior_safe_scan")
        return (base, "category_match")

    base = 0.95 - (position * 0.01)
    tags = category_tags_set(candidate)
    if tags_contain_any(tags, substitute_profile["beverage"]):
        base += 0.03
    deprioritized = tags_contain_any(tags, substitute_profile["deprioritize"])
    if deprioritized:
        base -= 0.10
    if prior_safe:
        return (min(base + 0.10, 0.99), "prior_safe_scan")
    if deprioritized:
        return (base, "substitute_category_cooking")
    return (base, "substitute_category")


def sql_str(value: str | None) -> str:
    if value is None:
        return "NULL"
    escaped = value.replace("'", "''")
    return f"'{escaped}'"


def json_str(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def main() -> int:
    products = parse_products(PRODUCTS_SQL)
    scans = parse_scans(SCANS_SQL)

    safe_by_profile: dict[int, set[str]] = defaultdict(set)
    for scan in scans:
        if scan.verdict == "SAFE":
            safe_by_profile[scan.profile_id].add(scan.barcode)

    by_category: dict[str, list[Product]] = defaultdict(list)
    for product in products.values():
        if eligible_candidate(product):
            by_category[product.category_en].append(product)

    for category_products in by_category.values():
        category_products.sort(key=lambda p: p.completeness, reverse=True)

    log_rows: list[str] = []
    log_id = 1
    tier_b_scans: list[tuple[int, int, str, list[dict]]] = []

    # Keep a few Tier-B demo scans with same-category LLM suggestions.
    tier_b_scan_ids = {3, 16, 35}

    for scan in scans:
        if scan.verdict not in ("WARNING", "UNSAFE"):
            continue

        source = products.get(scan.barcode)
        if source is None or not source.category_en or not source.ingredients_text:
            continue

        category = source.category_en
        substitute_profile = None
        pool = [
            p
            for p in by_category.get(category, [])
            if p.barcode != scan.barcode
            and not category_blocked(p, scan.profile_id)
            and not violates_strict_rules(p, scan.profile_id)
        ]

        if not pool:
            substitute_profile = substitute_profile_for(category)
            pool = substitute_candidates(products, source, scan.profile_id)

        if not pool:
            continue

        prior_safe_barcodes = safe_by_profile.get(scan.profile_id, set())
        prior_in_category = [p for p in pool if p.barcode in prior_safe_barcodes]
        other_in_category = [p for p in pool if p.barcode not in prior_safe_barcodes]

        def sort_key(product: Product) -> tuple[int, int, float]:
            prior = 1 if product.barcode in prior_safe_barcodes else 0
            return (label_boost(product, scan.profile_id), prior, product.completeness)

        ordered = sorted(prior_in_category + other_in_category, key=sort_key, reverse=True)
        # De-duplicate while preserving order
        seen: set[str] = set()
        deduped: list[Product] = []
        for product in ordered:
            if product.barcode in seen:
                continue
            seen.add(product.barcode)
            deduped.append(product)
        ordered = deduped

        picks = ordered[:MAX_ALTERNATIVES_PER_SCAN]
        if not picks:
            continue

        discovery_tier = (
            "TIER_B_LLM_DISCOVERY" if scan.scan_id in tier_b_scan_ids else "TIER_A_CATALOG"
        )

        for offset, candidate in enumerate(picks):
            prior = candidate.barcode in prior_safe_barcodes
            score, reason = rank_score(candidate, offset, prior, substitute_profile)
            if discovery_tier == "TIER_B_LLM_DISCOVERY" and offset == 0:
                reason = "llm_suggested_verified"
                score = max(score - 0.05, 0.75)

            data_quality = "VERIFIED" if candidate.completeness >= 0.5 else "PARTIAL"
            created = f"NOW() - INTERVAL {scan.days_ago} DAY + INTERVAL {5 + offset} MINUTE"

            log_rows.append(
                f"({log_id}, {scan.profile_id}, {scan.scan_id}, {sql_str(scan.barcode)}, "
                f"{sql_str(candidate.barcode)}, {sql_str(candidate.name)}, "
                f"{sql_str(candidate.brand)}, '{discovery_tier}', 'TIER_1_RULES', "
                f"{score:.4f}, {sql_str(reason)}, '{data_quality}', 'SAFE', 1, {created})"
            )
            log_id += 1

        if scan.scan_id in tier_b_scan_ids:
            tier_b_scans.append(
                (
                    scan.scan_id,
                    scan.profile_id,
                    scan.barcode,
                    [
                        {
                            "name": p.name,
                            "brand": p.brand,
                            "barcode": p.barcode,
                        }
                        for p in picks
                    ],
                )
            )

    ai_rows: list[str] = []
    ai_id = 1
    tier_b_meta = {
        3: (380, 95, 1420, 6),
        16: (420, 110, 1580, 15),
        35: (395, 88, 1310, 1),
    }
    for scan_id, profile_id, source_barcode, candidates in tier_b_scans:
        tokens = tier_b_meta.get(scan_id, (400, 100, 1500, 1))
        json_candidates = ", ".join(
            "{"
            + '"name":' + json_str(c["name"] or "")
            + ',"brand":' + json_str(c["brand"] or "")
            + ',"barcode":' + json_str(c["barcode"])
            + "}"
            for c in candidates
        )
        llm_json = sql_str(f'{{"candidates":[{json_candidates}]}}')
        ai_rows.append(
            f"({ai_id}, {scan_id}, {profile_id}, {sql_str(source_barcode)}, "
            f"'TIER_B_LLM_DISCOVERY', 'gpt-4o-mini', {tokens[0]}, {tokens[1]}, {tokens[2]}, "
            f"{llm_json}, {min(len(candidates), 1)}, "
            f"{max(len(candidates) - 1, 0)}, NOW() - INTERVAL {tokens[3]} DAY + INTERVAL 4 MINUTE)"
        )
        ai_id += 1

    lines = [
        "-- =============================================",
        "-- RECOMMENDATION LOGS (UC5 shown alternatives + UC17 history seed)",
        "-- Regenerated by scripts/generate_recommendation_logs_seed.py",
        "-- Rules: same main_category_en as source; ingredients required; keyword SAFE filter.",
        "-- =============================================",
        "INSERT INTO recommendation_logs (",
        "    id, profile_id, scan_id, source_barcode, recommended_barcode,",
        "    recommended_name, recommended_brand, discovery_tier, verification_tier,",
        "    rank_score, match_reason, data_quality, verdict_at_recommendation,",
        "    shown_to_user, created_at",
        ") VALUES",
        "",
    ]

    if log_rows:
        lines.append(",\n".join(log_rows) + ";")
    else:
        lines.append("-- (no recommendation rows generated)")

    lines.extend(
        [
            "",
            "-- =============================================",
            "-- RECOMMENDATION AI LOGS (Tier B LLM discovery audit seed)",
            "-- =============================================",
            "INSERT INTO recommendation_ai_logs (",
            "    id, scan_id, profile_id, source_barcode, execution_tier,",
            "    model_id, prompt_tokens, completion_tokens, latency_ms,",
            "    llm_candidates_json, candidates_accepted, candidates_rejected, created_at",
            ") VALUES",
            "",
        ]
    )

    if ai_rows:
        lines.append(",\n".join(ai_rows) + ";")
    else:
        lines.append("-- (no Tier B audit rows generated)")

    OUTPUT_SQL.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(log_rows)} recommendation_logs rows to {OUTPUT_SQL}")
    print(f"Wrote {len(ai_rows)} recommendation_ai_logs rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
