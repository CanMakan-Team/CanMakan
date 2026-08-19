#!/usr/bin/env python3
"""
Export catalog products from the backend SQL seed into JSON for offline ML batch jobs.

Reads server/backend/src/main/resources/01_products.sql by default and writes a
products.json array with the fields needed for TF-IDF vectorization.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

# Column order matches 01_products.sql INSERT header.
PRODUCT_COLUMNS = [
    "barcode",
    "product_name",
    "generic_name",
    "brand",
    "quantity",
    "serving_size",
    "serving_quantity",
    "categories",
    "category_tags",
    "main_category",
    "main_category_en",
    "food_groups",
    "food_groups_tags",
    "ingredients_text",
    "ingredients_analysis_tags",
    "allergens",
    "allergens_en",
    "traces_tags",
    "traces_en",
    "labels_tags",
    "labels_en",
    "countries_tags",
    "image_url",
    "nutrition_grade",
    "no_nutrition_data",
    "completeness",
    "energy_kcal_100g",
    "energy_kj_100g",
    "sugars_100g",
    "added_sugars_100g",
    "proteins_100g",
    "carbohydrates_100g",
    "fat_100g",
    "saturated_fat_100g",
    "trans_fat_100g",
    "cholesterol_100g",
    "fiber_100g",
    "sodium_100g",
    "salt_100g",
    "added_salt_100g",
    "alcohol_100g",
]

INSERT_PREFIX = "INSERT INTO products ("
TUPLE_PATTERN = re.compile(r"\(([^()]*(?:\([^()]*\)[^()]*)*)\)")


def _strip_sql_string(value: str) -> Any:
    value = value.strip()
    if value.upper() == "NULL":
        return None
    if value.startswith("'") and value.endswith("'"):
        return value[1:-1].replace("''", "'")
    if re.fullmatch(r"-?\d+(?:\.\d+)?", value):
        if "." in value:
            return float(value)
        return int(value)
    return value


def _split_sql_tuple(raw: str) -> list[Any]:
    parts: list[str] = []
    current: list[str] = []
    in_string = False
    i = 0
    while i < len(raw):
        char = raw[i]
        if char == "'" and not in_string:
            in_string = True
            current.append(char)
        elif char == "'" and in_string:
            if i + 1 < len(raw) and raw[i + 1] == "'":
                current.append("''")
                i += 1
            else:
                in_string = False
                current.append(char)
        elif char == "," and not in_string:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
        i += 1
    if current:
        parts.append("".join(current).strip())
    return [_strip_sql_string(part) for part in parts]


def parse_products_sql(sql_text: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for match in TUPLE_PATTERN.finditer(sql_text):
        values = _split_sql_tuple(match.group(1))
        if len(values) != len(PRODUCT_COLUMNS):
            continue
        row = dict(zip(PRODUCT_COLUMNS, values))
        rows.append(
            {
                "barcode": row["barcode"],
                "product_name": row["product_name"],
                "brand": row["brand"],
                "main_category_en": row["main_category_en"],
                "category_tags": row["category_tags"],
                "categories": row["categories"],
                "labels_tags": row["labels_tags"],
                "labels_en": row["labels_en"],
                "ingredients_text": row["ingredients_text"],
                "sugars_100g": row["sugars_100g"],
                "sodium_100g": row["sodium_100g"],
            }
        )
    return rows


def default_seed_path() -> Path:
    repo_root = Path(__file__).resolve().parents[3]
    return repo_root / "server" / "backend" / "src" / "main" / "resources" / "01_products.sql"


def main() -> None:
    parser = argparse.ArgumentParser(description="Export catalog products for offline ML.")
    parser.add_argument(
        "--input",
        type=Path,
        default=default_seed_path(),
        help="Path to 01_products.sql seed file",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/products.json"),
        help="Output JSON path",
    )
    args = parser.parse_args()

    sql_text = args.input.read_text(encoding="utf-8")
    if INSERT_PREFIX not in sql_text:
        raise SystemExit(f"Expected products INSERT in {args.input}")

    products = parse_products_sql(sql_text)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(products, indent=2), encoding="utf-8")
    print(f"Exported {len(products)} products to {args.output}")


if __name__ == "__main__":
    main()
