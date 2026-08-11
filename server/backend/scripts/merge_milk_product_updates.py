#!/usr/bin/env python3
"""
Merge milk product corrections from Update_milk-product.csv into 01_products.sql.

Updates product_name, ingredients_text, allergens, and category fields
(categories, category_tags, main_category, main_category_en) for matching barcodes.
"""

from __future__ import annotations

import csv
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

DEFAULT_CSV = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/Update_milk-product.csv"
)
DEFAULT_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)

CATEGORY_TO_TAG = {
    "Fresh milks": "en:fresh-milks",
    "UHT milks": "en:uht-milks",
    "Whole milks": "en:whole-milks",
    "Whole pasteurised milks": "en:whole-pasteurised-milks",
    "Whole milk UHT": "en:whole-milk-uht",
    "Skimmed milks": "en:skimmed-milks",
    "Milks": "en:milks",
    "Pasteurised milks": "en:pasteurised-milks",
}

MILK_FOOD_GROUPS = "en:milk-and-yogurt"
MILK_FOOD_GROUPS_TAGS = "en:milk-and-dairy-products,en:milk-and-yogurt"

FIELD_PRODUCT_NAME = 1
FIELD_CATEGORIES = 7
FIELD_CATEGORY_TAGS = 8
FIELD_MAIN_CATEGORY = 9
FIELD_MAIN_CATEGORY_EN = 10
FIELD_FOOD_GROUPS = 11
FIELD_FOOD_GROUPS_TAGS = 12
FIELD_INGREDIENTS_TEXT = 13
FIELD_ALLERGENS = 15

NUMERIC_FIELD_INDICES = {6, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40}


@dataclass
class ProductUpdate:
    product_name: str
    ingredients_text: str | None
    allergens: str | None
    main_category_en: str


def parse_category_fields(main_category_en_csv: str) -> tuple[str, str, str, str]:
    parts = [part.strip() for part in main_category_en_csv.split(",") if part.strip()]
    if not parts:
        raise ValueError("main_category_en is empty")

    primary_en = parts[0]
    primary_tag = CATEGORY_TO_TAG.get(primary_en)
    if primary_tag is None:
        raise ValueError(f"No tag mapping for category: {primary_en!r}")

    categories = "Dairies, Milks, " + ", ".join(parts)
    tag_parts = ["en:dairies", "en:milks"]
    for part in parts:
        tag = CATEGORY_TO_TAG.get(part)
        if tag and tag not in tag_parts:
            tag_parts.append(tag)

    return categories, ",".join(tag_parts), primary_tag, primary_en


def load_updates(csv_path: Path) -> dict[str, ProductUpdate]:
    updates: dict[str, ProductUpdate] = {}
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"barcode", "product_name", "ingredients_text", "allergens", "main_category_en"}
        if not reader.fieldnames or not required.issubset(set(reader.fieldnames)):
            raise ValueError(f"CSV must contain columns: {sorted(required)}")

        for row in reader:
            barcode = (row.get("barcode") or "").strip()
            if not barcode:
                continue

            ingredients = (row.get("ingredients_text") or "").strip()
            allergens = (row.get("allergens") or "").strip()
            updates[barcode] = ProductUpdate(
                product_name=(row.get("product_name") or "").strip(),
                ingredients_text=ingredients if ingredients and ingredients.upper() != "NULL" else None,
                allergens=allergens if allergens and allergens.upper() != "NULL" else None,
                main_category_en=(row.get("main_category_en") or "").strip(),
            )
    return updates


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


def format_sql_value(value: str | None, field_index: int) -> str:
    if value is None:
        return "NULL"
    if field_index in NUMERIC_FIELD_INDICES and re.fullmatch(r"-?\d+(\.\d+)?", value):
        return value
    escaped = value.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def format_sql_tuple(values: list[str | None]) -> str:
    return "(" + ", ".join(
        format_sql_value(value, index) for index, value in enumerate(values)
    ) + ")"


def extract_tuple_line(line: str) -> tuple[str, str] | None:
    stripped = line.strip()
    if not stripped.startswith("('"):
        return None
    if not (stripped.endswith("),") or stripped.endswith(");")):
        return None

    body = stripped[1:-2]
    return stripped, body


def apply_update(values: list[str | None], update: ProductUpdate) -> None:
    categories, category_tags, main_category, main_category_en = parse_category_fields(
        update.main_category_en
    )

    values[FIELD_PRODUCT_NAME] = update.product_name
    values[FIELD_CATEGORIES] = categories
    values[FIELD_CATEGORY_TAGS] = category_tags
    values[FIELD_MAIN_CATEGORY] = main_category
    values[FIELD_MAIN_CATEGORY_EN] = main_category_en
    values[FIELD_FOOD_GROUPS] = MILK_FOOD_GROUPS
    values[FIELD_FOOD_GROUPS_TAGS] = MILK_FOOD_GROUPS_TAGS

    if update.ingredients_text is not None:
        values[FIELD_INGREDIENTS_TEXT] = update.ingredients_text
    if update.allergens is not None:
        values[FIELD_ALLERGENS] = update.allergens


def update_products_sql(
    products_sql_path: Path,
    updates: dict[str, ProductUpdate],
) -> tuple[dict[str, int], set[str]]:
    stats = {"updated": 0, "missing_in_seed": 0, "parse_errors": 0}
    missing_barcodes = set(updates.keys())
    output_lines: list[str] = []
    tuple_line_pattern = re.compile(r"^\('([^']+)'")

    with products_sql_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            match = tuple_line_pattern.match(line.strip())
            if not match:
                output_lines.append(line)
                continue

            barcode = match.group(1)
            if barcode not in updates:
                output_lines.append(line)
                continue

            missing_barcodes.discard(barcode)
            extracted = extract_tuple_line(line)
            if extracted is None:
                stats["parse_errors"] += 1
                output_lines.append(line)
                continue

            _, tuple_body = extracted
            try:
                values = parse_sql_tuple_values(tuple_body)
            except ValueError:
                stats["parse_errors"] += 1
                output_lines.append(line)
                continue

            if len(values) <= FIELD_ALLERGENS:
                stats["parse_errors"] += 1
                output_lines.append(line)
                continue

            apply_update(values, updates[barcode])
            rebuilt = format_sql_tuple(values)
            if line.strip().endswith("),"):
                output_lines.append(rebuilt + ",\n")
            else:
                output_lines.append(rebuilt + ";\n")
            stats["updated"] += 1

    stats["missing_in_seed"] = len(missing_barcodes)
    products_sql_path.write_text("".join(output_lines), encoding="utf-8")
    return stats, missing_barcodes


def main() -> int:
    csv_path = Path(DEFAULT_CSV)
    products_sql_path = Path(DEFAULT_PRODUCTS_SQL)

    if not csv_path.is_file():
        print(f"ERROR: CSV not found: {csv_path}", file=sys.stderr)
        return 1
    if not products_sql_path.is_file():
        print(f"ERROR: products SQL not found: {products_sql_path}", file=sys.stderr)
        return 1

    updates = load_updates(csv_path)
    print(f"Loaded {len(updates)} product updates from {csv_path}")

    backup_path = products_sql_path.with_suffix(products_sql_path.suffix + ".bak")
    shutil.copy2(products_sql_path, backup_path)
    print(f"Backup created: {backup_path}")

    stats, missing_barcodes = update_products_sql(products_sql_path, updates)
    print(f"Updated file: {products_sql_path}")
    for key, value in stats.items():
        print(f"  {key}: {value}")
    if missing_barcodes:
        print("  missing barcodes:", ", ".join(sorted(missing_barcodes)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
