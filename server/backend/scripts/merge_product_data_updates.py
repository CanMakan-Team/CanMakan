#!/usr/bin/env python3
"""
Merge product corrections from Update_product_data.csv into 01_products.sql.

Updates product_name, ingredients_text, allergens, allergens_en, and category fields
for matching barcodes. Scientific-notation barcodes from Excel are resolved by product
name (and barcode prefix when needed).
"""

from __future__ import annotations

import csv
import re
import shutil
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

DEFAULT_CSV = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/Update_product_data.csv"
)
DEFAULT_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)

CATEGORY_EN_TO_TAG = {
    "fresh milks": "en:fresh-milks",
    "uht milks": "en:uht-milks",
    "whole milks": "en:whole-milks",
    "whole pasteurised milks": "en:whole-pasteurised-milks",
    "whole milk uht": "en:whole-milk-uht",
    "skimmed milks": "en:skimmed-milks",
    "uht skimmed milks": "en:uht-skimmed-milks",
    "milks": "en:milks",
    "pasteurised milks": "en:pasteurised-milks",
    "flavoured milks": "en:flavoured-milks",
    "strawberry milks": "en:strawberry-milks",
    "milk teas": "en:milk-teas",
    "wheat flours": "en:wheat-flours",
    "ice creams": "en:ice-creams",
    "ice cream tubs": "en:ice-cream-tubs",
    "breads": "en:breads",
    "breadsticks": "en:breadsticks",
    "sauces": "en:sauces",
    "biscuits and cookies": "en:biscuits-and-cookies",
    "cheeses": "en:cheeses",
    "milk powder": "en:milk-powder",
    "plant-based milk alternatives": "en:plant-based-milk-alternatives",
    "lactose free milk": "en:lactose-free-milk",
    "breakfast cereals": "en:breakfast-cereals",
}

DAIRY_CATEGORY_EN = {
    "fresh milks",
    "uht milks",
    "whole milks",
    "whole pasteurised milks",
    "whole milk uht",
    "skimmed milks",
    "uht skimmed milks",
    "milks",
    "pasteurised milks",
    "flavoured milks",
    "strawberry milks",
    "milk teas",
    "milk powder",
    "lactose free milk",
}

FIELD_PRODUCT_NAME = 1
FIELD_CATEGORIES = 7
FIELD_CATEGORY_TAGS = 8
FIELD_MAIN_CATEGORY = 9
FIELD_MAIN_CATEGORY_EN = 10
FIELD_FOOD_GROUPS = 11
FIELD_FOOD_GROUPS_TAGS = 12
FIELD_INGREDIENTS_TEXT = 13
FIELD_ALLERGENS = 15
FIELD_ALLERGENS_EN = 16

NUMERIC_FIELD_INDICES = {6, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40}


@dataclass
class ProductUpdate:
    product_name: str
    category_tags: str | None
    main_category: str | None
    main_category_en: str | None
    ingredients_text: str | None
    allergens: str | None
    allergens_en: str | None


def normalize_optional(value: str | None) -> str | None:
    text = (value or "").strip()
    if not text or text.upper() == "NULL":
        return None
    return text


def normalize_name(value: str | None) -> str:
    return re.sub(r"\s+", " ", (value or "").strip().lower())


def normalize_barcode_raw(raw: str | None) -> tuple[str | None, bool]:
    text = (raw or "").strip()
    if not text or text.upper() == "NULL":
        return None, False
    if re.search(r"[eE][+-]?\d+", text):
        try:
            return str(int(float(text))), True
        except ValueError:
            return text, True
    if re.fullmatch(r"\d+\.0+", text):
        return text.split(".")[0], False
    return text, False


def barcode_prefix_from_scientific(raw: str) -> str | None:
    match = re.match(r"(\d+\.?\d*)[eE][+-]?\d+", raw.strip())
    if not match:
        return None
    digits = match.group(1).replace(".", "")
    return digits[:6] if len(digits) >= 6 else digits


def tag_to_label(tag: str) -> str:
    slug = tag[3:] if tag.startswith("en:") else tag
    special = {
        "plant-based-foods-and-beverages": "Plant-based foods and beverages",
        "plant-based-foods": "Plant-based foods",
        "cereals-and-potatoes": "Cereals and potatoes",
        "cereals-and-their-products": "Cereals and their products",
        "cereal-flours": "Cereal flours",
        "wheat-flours": "Wheat flours",
        "fresh-milks": "Fresh milks",
        "uht-milks": "UHT milks",
        "whole-milks": "Whole milks",
        "flavoured-milks": "Flavoured milks",
        "milk-teas": "Milk teas",
        "ice-creams": "Ice creams",
        "ice-cream-tubs": "Ice cream tubs",
        "roasted-peanuts": "Roasted peanuts",
        "breakfast-cereals": "Breakfast cereals",
    }
    if slug in special:
        return special[slug]
    return slug.replace("-", " ").capitalize()


def tags_to_categories(category_tags: str) -> str:
    return ", ".join(tag_to_label(tag.strip()) for tag in category_tags.split(",") if tag.strip())


def tags_to_allergens_en(allergens: str) -> str | None:
    tags = [tag.strip() for tag in allergens.split(",") if tag.strip()]
    if not tags:
        return None
    if all(tag.startswith("en:") for tag in tags):
        return ",".join(tag_to_label(tag) for tag in tags)
    return allergens


def normalize_main_category_tag(raw: str | None) -> str | None:
    text = normalize_optional(raw)
    if text is None:
        return None
    if text.startswith("en:"):
        return text
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return f"en:{slug}" if slug else None


def resolve_main_category_from_en(main_category_en: str) -> str:
    lookup = CATEGORY_EN_TO_TAG.get(main_category_en.strip().lower())
    if lookup:
        return lookup
    slug = re.sub(r"[^a-z0-9]+", "-", main_category_en.strip().lower()).strip("-")
    return f"en:{slug}"


def build_categories_from_en(main_category_en: str) -> tuple[str, str, str, str, str | None, str | None]:
    primary_en = main_category_en.strip()
    primary_tag = resolve_main_category_from_en(primary_en)
    key = primary_en.lower()

    if key in DAIRY_CATEGORY_EN:
        categories = "Dairies, Milks, " + primary_en
        tag_parts = ["en:dairies", "en:milks", primary_tag]
        return (
            categories,
            ",".join(tag_parts),
            primary_tag,
            primary_en,
            "en:milk-and-yogurt",
            "en:milk-and-dairy-products,en:milk-and-yogurt",
        )

    if key == "wheat flours":
        category_tags = (
            "en:plant-based-foods-and-beverages,en:plant-based-foods,en:cereals-and-potatoes,"
            "en:cereals-and-their-products,en:flours,en:cereal-flours,en:wheat-flours"
        )
        return (
            tags_to_categories(category_tags),
            category_tags,
            primary_tag,
            primary_en,
            "en:cereals",
            "en:cereals-and-potatoes,en:cereals",
        )

    categories = primary_en
    category_tags = primary_tag
    return categories, category_tags, primary_tag, primary_en, None, None


def resolve_food_groups(category_tags: str) -> tuple[str | None, str | None]:
    tags = {tag.strip() for tag in category_tags.split(",") if tag.strip()}
    if tags & {"en:dairies", "en:milks", "en:fresh-milks", "en:uht-milks", "en:flavoured-milks"}:
        return "en:milk-and-yogurt", "en:milk-and-dairy-products,en:milk-and-yogurt"
    if "en:fruits-and-vegetables-based-foods" in tags or "en:dried-fruits" in tags:
        return "en:fruits", "en:fruits-and-vegetables-based-foods,en:fruits"
    if "en:cereals-and-potatoes" in tags or "en:wheat-flours" in tags:
        return "en:cereals", "en:cereals-and-potatoes,en:cereals"
    if "en:ice-creams" in tags or "en:ice-cream-tubs" in tags:
        return "en:ice-cream", "en:milk-and-dairy-products,en:ice-cream"
    if "en:nuts" in tags or "en:peanuts" in tags or "en:roasted-peanuts" in tags:
        return "en:nuts", "en:salty-snacks,en:nuts"
    if "en:grocery" in tags:
        return "0", "0"
    return None, None


def load_seed_name_index(products_sql_path: Path) -> dict[str, list[str]]:
    index: dict[str, list[str]] = defaultdict(list)
    pattern = re.compile(r"^\('([^']+)',\s*'((?:''|[^'])*)'")
    with products_sql_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if match:
                barcode = match.group(1)
                name = match.group(2).replace("''", "'")
                index[normalize_name(name)].append(barcode)
    return index


def resolve_barcode(raw_barcode: str, product_name: str, seed_by_name: dict[str, list[str]]) -> tuple[str | None, str]:
    normalized, is_scientific = normalize_barcode_raw(raw_barcode)
    if normalized is None:
        return None, "empty_barcode"

    if not is_scientific:
        return normalized, "plain"

    candidates = seed_by_name.get(normalize_name(product_name), [])
    if not candidates:
        return None, "unresolved_name"
    if len(candidates) == 1:
        return candidates[0], "resolved_name"

    prefix = barcode_prefix_from_scientific(raw_barcode)
    if prefix:
        filtered = [barcode for barcode in candidates if barcode.startswith(prefix)]
        if len(filtered) == 1:
            return filtered[0], "resolved_name_prefix"
        if len(filtered) > 1:
            return None, "ambiguous_name_prefix"

    return None, "ambiguous_name"


def load_updates(
    csv_path: Path,
    seed_by_name: dict[str, list[str]],
) -> tuple[dict[str, ProductUpdate], dict[str, int]]:
    updates: dict[str, ProductUpdate] = {}
    resolution_stats: dict[str, int] = defaultdict(int)

    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            raise ValueError("CSV has no header row")

        for row in reader:
            raw_barcode = (row.get("barcode") or "").strip()
            if not raw_barcode:
                continue

            product_name = (row.get("product_name") or "").strip()
            barcode, resolution = resolve_barcode(raw_barcode, product_name, seed_by_name)
            resolution_stats[resolution] += 1
            if barcode is None:
                continue

            ingredients_key = "ingredients text"
            if ingredients_key not in row and "ingredients_text" in row:
                ingredients_key = "ingredients_text"

            allergens = normalize_optional(row.get("allergens"))
            allergens_en = normalize_optional(row.get("allergens_en"))
            if allergens_en and allergens_en.startswith("en:"):
                allergens_en = tags_to_allergens_en(allergens_en)

            updates[barcode] = ProductUpdate(
                product_name=product_name,
                category_tags=normalize_optional(row.get("category_tags")),
                main_category=normalize_optional(row.get("main_category")),
                main_category_en=normalize_optional(row.get("main_category_en")),
                ingredients_text=normalize_optional(row.get(ingredients_key)),
                allergens=allergens,
                allergens_en=allergens_en,
            )

    return updates, resolution_stats


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
    return stripped, stripped[1:-2]


def apply_update(values: list[str | None], update: ProductUpdate) -> None:
    values[FIELD_PRODUCT_NAME] = update.product_name

    if update.category_tags:
        categories = tags_to_categories(update.category_tags)
        main_category = normalize_main_category_tag(update.main_category)
        if main_category is None and update.main_category_en:
            main_category = resolve_main_category_from_en(update.main_category_en)
        elif main_category is None:
            meaningful = [
                tag.strip()
                for tag in update.category_tags.split(",")
                if tag.strip() and tag.strip() not in {"en:no-gluten", "en:gluten-free", "en:grocery"}
            ]
            main_category = meaningful[-1] if meaningful else None

        main_category_en = update.main_category_en
        if main_category_en is None and main_category:
            main_category_en = tag_to_label(main_category)

        food_groups, food_groups_tags = resolve_food_groups(update.category_tags)

        values[FIELD_CATEGORIES] = categories
        values[FIELD_CATEGORY_TAGS] = update.category_tags
        if main_category:
            values[FIELD_MAIN_CATEGORY] = main_category
        if main_category_en:
            values[FIELD_MAIN_CATEGORY_EN] = main_category_en
        if food_groups is not None:
            values[FIELD_FOOD_GROUPS] = food_groups
        if food_groups_tags is not None:
            values[FIELD_FOOD_GROUPS_TAGS] = food_groups_tags
    elif update.main_category_en:
        categories, category_tags, main_category, main_category_en, food_groups, food_groups_tags = (
            build_categories_from_en(update.main_category_en)
        )
        csv_main_category = normalize_main_category_tag(update.main_category)
        if csv_main_category:
            main_category = csv_main_category

        values[FIELD_CATEGORIES] = categories
        values[FIELD_CATEGORY_TAGS] = category_tags
        values[FIELD_MAIN_CATEGORY] = main_category
        values[FIELD_MAIN_CATEGORY_EN] = main_category_en
        if food_groups is not None:
            values[FIELD_FOOD_GROUPS] = food_groups
        if food_groups_tags is not None:
            values[FIELD_FOOD_GROUPS_TAGS] = food_groups_tags

    if update.ingredients_text is not None:
        values[FIELD_INGREDIENTS_TEXT] = update.ingredients_text
    if update.allergens is not None:
        values[FIELD_ALLERGENS] = update.allergens
    if update.allergens_en is not None:
        values[FIELD_ALLERGENS_EN] = update.allergens_en
    elif update.allergens is not None:
        derived = tags_to_allergens_en(update.allergens)
        if derived:
            values[FIELD_ALLERGENS_EN] = derived


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
            values = parse_sql_tuple_values(tuple_body)
            if len(values) <= FIELD_ALLERGENS_EN:
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

    seed_by_name = load_seed_name_index(products_sql_path)
    updates, resolution_stats = load_updates(csv_path, seed_by_name)
    print(f"Loaded {len(updates)} resolvable product updates from {csv_path}")
    for key, value in sorted(resolution_stats.items()):
        print(f"  barcode {key}: {value}")

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
