#!/usr/bin/env python3
"""Audit substitute-tag coverage and emit additive UPDATE SQL.

Reads 01_products.sql (or artifacts/products.json) and:
1. Writes artifacts/tag_coverage.json with current vs eligible counts
2. Writes 01c_recommendation_substitute_tags.sql that APPENDs missing
   substitute tags when labels already indicate eligibility
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_products import default_seed_path, parse_products_sql

GLUTEN_LABELS = frozenset({"en:no-gluten", "en:certified-gluten-free"})
DAIRY_FREE_LABELS = frozenset({"en:vegan", "en:without-addition-of-dairy-products"})
LOW_SALT_LABELS = frozenset(
    {"en:low-salt", "en:no-salt-added", "en:no-added-salt", "en:low-sodium"}
)

SUBSTITUTE_TAGS = [
    "Gluten Free sauces",
    "Gluten free bread",
    "Gluten free Breakfast cereals",
    "ice-creams-and-sorbets",
    "en:ice-creams-and-sorbets",
    "Low sodium sauces",
    "en:milk-substitutes",
    "en:dairy-substitutes",
    "en:gluten-free-flour",
]


def _split_tags(value: Any) -> set[str]:
    if not value or not isinstance(value, str):
        return set()
    return {part.strip() for part in value.split(",") if part.strip()}


def _haystack(product: dict[str, Any]) -> str:
    parts = [
        product.get("product_name"),
        product.get("brand"),
        product.get("main_category_en"),
        product.get("category_tags"),
        product.get("categories"),
        product.get("labels_en"),
        product.get("labels_tags"),
    ]
    return " ".join(str(part).lower() for part in parts if part)


def _has_any_label(labels: set[str], needles: frozenset[str]) -> bool:
    return not labels.isdisjoint(needles)


def _is_breakfast_cereal(haystack: str) -> bool:
    """Avoid matching OFF taxonomy paths like en:cereals-and-potatoes."""
    if "breakfast cereal" in haystack or "breakfast-cereal" in haystack:
        return True
    if "en:breakfast-cereals" in haystack:
        return True
    return "cereal" in haystack and "spread" not in haystack and "tahini" not in haystack


def suggested_tags(product: dict[str, Any]) -> list[str]:
    haystack = _haystack(product)
    labels = _split_tags(product.get("labels_tags"))
    labels_en = (product.get("labels_en") or "").lower()
    gluten = _has_any_label(labels, GLUTEN_LABELS) or "no gluten" in labels_en or "gluten-free" in labels_en or "gluten free" in labels_en
    dairy_free = _has_any_label(labels, DAIRY_FREE_LABELS) or "vegan" in labels_en
    low_salt = _has_any_label(labels, LOW_SALT_LABELS) or "no salt added" in labels_en or "low salt" in labels_en or "low sodium" in labels_en

    suggestions: list[str] = []
    is_soy_sauce = "soy sauce" in haystack or "soya sauce" in haystack or "soy sauces" in haystack
    is_sauce = "sauce" in haystack
    is_bread = any(token in haystack for token in ("bread", "bun", "roll"))
    is_cereal = _is_breakfast_cereal(haystack)
    is_ice_cream = "ice cream" in haystack or "ice-cream" in haystack or "sorbet" in haystack
    is_flour = "flour" in haystack

    if gluten and (is_soy_sauce or is_sauce) and not is_ice_cream:
        suggestions.append("Gluten Free sauces")
    # Breakfast cereal must not receive bread substitute tags (oat mis-tags).
    if gluten and is_bread and not is_cereal:
        suggestions.append("Gluten free bread")
    if gluten and is_cereal:
        suggestions.append("Gluten free Breakfast cereals")
    if gluten and is_flour and not is_cereal and not is_bread:
        suggestions.append("en:gluten-free-flour")
    if dairy_free and is_ice_cream:
        suggestions.append("ice-creams-and-sorbets")
    if low_salt and is_sauce and not is_ice_cream:
        suggestions.append("Low sodium sauces")
    return suggestions


def sql_literal(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def build_update_sql(barcode: str, tag: str) -> str:
    escaped_barcode = sql_literal(barcode)
    escaped_tag = sql_literal(tag)
    like_needle = sql_literal("%," + tag + ",%")
    return f"""UPDATE products
SET category_tags = CASE
  WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN {escaped_tag}
  WHEN CONCAT(',', category_tags, ',') LIKE {like_needle} THEN category_tags
  ELSE CONCAT(category_tags, ',', {escaped_tag})
END
WHERE barcode = {escaped_barcode};
"""


def analyze(products: list[dict[str, Any]]) -> tuple[dict[str, Any], list[tuple[str, str]]]:
    tagged_counts: dict[str, int] = defaultdict(int)
    eligible_missing: dict[str, list[str]] = defaultdict(list)
    updates: list[tuple[str, str]] = []

    for product in products:
        barcode = str(product.get("barcode") or "")
        if not barcode:
            continue
        existing = _split_tags(product.get("category_tags"))
        for tag in SUBSTITUTE_TAGS:
            if tag in existing:
                tagged_counts[tag] += 1
        for tag in suggested_tags(product):
            if tag not in existing:
                eligible_missing[tag].append(barcode)
                updates.append((barcode, tag))

    coverage = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "product_count": len(products),
        "substitute_tags": {
            tag: {
                "already_tagged": tagged_counts.get(tag, 0),
                "eligible_missing": len(eligible_missing.get(tag, [])),
                "eligible_missing_barcodes": eligible_missing.get(tag, [])[:50],
            }
            for tag in SUBSTITUTE_TAGS
        },
        "updates_to_apply": len(updates),
    }
    return coverage, updates


def write_sql(path: Path, updates: list[tuple[str, str]]) -> None:
    lines = [
        "-- Additive UC5 substitute tags inferred from labels_tags / labels_en.",
        "-- Generated by server/machine-learning/scripts/audit_substitute_tags.py",
        "-- Does not rewrite 01_products.sql INSERT rows.",
        "",
    ]
    if not updates:
        lines.append("-- No eligible missing tags found.")
        lines.append("")
    for barcode, tag in updates:
        lines.append(build_update_sql(barcode, tag))
    path.write_text("\n".join(lines), encoding="utf-8")


def load_products(input_path: Path) -> list[dict[str, Any]]:
    if input_path.suffix.lower() == ".json":
        data = json.loads(input_path.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return data
        raise SystemExit(f"Expected a JSON array in {input_path}")
    return parse_products_sql(input_path.read_text(encoding="utf-8"))


def default_sql_output() -> Path:
    repo_root = Path(__file__).resolve().parents[3]
    return repo_root / "server" / "backend" / "src" / "main" / "resources" / "01c_recommendation_substitute_tags.sql"


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit and backfill UC5 substitute tags.")
    parser.add_argument("--input", type=Path, default=default_seed_path())
    parser.add_argument("--coverage-output", type=Path, default=Path("artifacts/tag_coverage.json"))
    parser.add_argument("--sql-output", type=Path, default=default_sql_output())
    args = parser.parse_args()

    products = load_products(args.input)
    coverage, updates = analyze(products)

    args.coverage_output.parent.mkdir(parents=True, exist_ok=True)
    args.coverage_output.write_text(json.dumps(coverage, indent=2), encoding="utf-8")
    write_sql(args.sql_output, updates)

    print(f"Audited {len(products)} products")
    print(f"Coverage report: {args.coverage_output}")
    print(f"SQL updates ({len(updates)}): {args.sql_output}")


if __name__ == "__main__":
    main()
