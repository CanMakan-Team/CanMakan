#!/usr/bin/env python3
"""
Merge flour product corrections from update_flour.csv into 01_products.sql.

Updates product_name, brand, ingredients_text, allergens, and category fields
(categories, category_tags, main_category, main_category_en, food_groups, food_groups_tags).
"""

from __future__ import annotations

import csv
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

DEFAULT_CSV = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/update_flour.csv"
)
DEFAULT_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)

WHEAT_FLOUR_TAGS = (
    "en:plant-based-foods-and-beverages,en:plant-based-foods,en:cereals-and-potatoes,"
    "en:cereals-and-their-products,en:flours,en:cereal-flours,en:wheat-flours"
)

MAIN_CATEGORY_EN_TO_TAG = {
    "wheat flours": "en:wheat-flours",
    "corn starch": "en:corn-starch",
    "corn flour": "en:corn-starch",
    "brown rice flour": "en:brown-rice-flour",
    "organic buckwheat flour": "en:buckwheat-flour",
    "dried coconut flour": "en:dried-coconut-flour",
    "amaranath flour": "en:amaranth-flour",
    "oat flour": "en:oat-flour",
}

GLUTEN_FREE_SUFFIX = (
    "en:no-gluten,en:gluten-free,en:gluten-free-flour"
)

FIELD_PRODUCT_NAME = 1
FIELD_BRAND = 3
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
    brand: str | None
    main_category_en: str
    allergens: str | None
    category_tags: str
    ingredients_text: str | None


def normalize_optional(value: str | None) -> str | None:
    text = (value or "").strip()
    if not text or text.upper() == "NULL":
        return None
    return text


def normalize_category_tags(raw_tags: str) -> str:
    tags = [tag.strip() for tag in raw_tags.split(",") if tag.strip()]
    if not tags:
        raise ValueError("category_tags is empty")

    if tags == ["en:wheat-flours"]:
        return WHEAT_FLOUR_TAGS

    normalized: list[str] = []
    for tag in tags:
        tag = tag.replace(" ", "")
        if tag == "en:corn-starch":
            tag = "en:corn-starch"
        if tag not in normalized:
            normalized.append(tag)
    return ",".join(normalized)


def tag_to_label(tag: str) -> str:
    slug = tag[3:] if tag.startswith("en:") else tag
    special = {
        "plant-based-foods-and-beverages": "Plant-based foods and beverages",
        "plant-based-foods": "Plant-based foods",
        "cereals-and-potatoes": "Cereals and potatoes",
        "cereals-and-their-products": "Cereals and their products",
        "cereal-flours": "Cereal flours",
        "wheat-flours": "Wheat flours",
        "bread-flours": "Bread flours",
        "white-wheat-flours": "White wheat flours",
        "corn-starch": "Corn starch",
        "cereal-starches": "Cereal starches",
        "starches": "Starches",
        "dried-coconut-flour": "Dried coconut flour",
        "dried-coconut": "Dried coconut",
        "dried-fruits": "Dried fruits",
        "dried-plant-based-foods": "Dried plant-based foods",
        "fruits-based-foods": "Fruits based foods",
        "fruits-and-vegetables-based-foods": "Fruits and vegetables based foods",
        "gluten-free-flour": "Gluten-free flour",
        "no-gluten": "No gluten",
        "gluten-free": "Gluten-free",
    }
    if slug in special:
        return special[slug]
    return slug.replace("-", " ").capitalize()


def tags_to_categories(category_tags: str) -> str:
    return ", ".join(tag_to_label(tag.strip()) for tag in category_tags.split(",") if tag.strip())


def resolve_main_category(main_category_en: str, category_tags: str) -> str:
    normalized_en = main_category_en.strip()
    lookup = MAIN_CATEGORY_EN_TO_TAG.get(normalized_en.lower())
    if lookup:
        return lookup

    target = normalized_en.lower()
    for tag in category_tags.split(","):
        tag = tag.strip()
        if tag_to_label(tag).lower() == target:
            return tag if tag.startswith("en:") else f"en:{tag}"

    meaningful_tags = [
        tag.strip()
        for tag in category_tags.split(",")
        if tag.strip()
        and tag.strip() not in {"en:no-gluten", "en:gluten-free", "en:gluten-free-flour", "en:grocery"}
    ]
    if meaningful_tags:
        last_tag = meaningful_tags[-1]
        return last_tag if last_tag.startswith("en:") else f"en:{last_tag}"

    slug = re.sub(r"[^a-z0-9]+", "-", normalized_en.lower()).strip("-")
    return f"en:{slug}"


def resolve_food_groups(category_tags: str) -> tuple[str | None, str | None]:
    tags = {tag.strip() for tag in category_tags.split(",") if tag.strip()}
    if "en:fruits-and-vegetables-based-foods" in tags or "en:dried-fruits" in tags:
        return "en:fruits", "en:fruits-and-vegetables-based-foods,en:fruits"
    if "en:cereals-and-potatoes" in tags or "en:wheat-flours" in tags or "en:corn-starch" in tags:
        return "en:cereals", "en:cereals-and-potatoes,en:cereals"
    if "en:grocery" in tags:
        return "0", "0"
    return None, None


def load_updates(csv_path: Path) -> dict[str, ProductUpdate]:
    updates: dict[str, ProductUpdate] = {}
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {
            "barcode",
            "product_name",
            "main_category_en",
            "allergens",
            "category_tags",
            "ingredients_text",
            "brand",
        }
        if not reader.fieldnames or not required.issubset(set(reader.fieldnames)):
            raise ValueError(f"CSV must contain columns: {sorted(required)}")

        for row in reader:
            barcode = (row.get("barcode") or "").strip()
            if not barcode:
                continue

            main_category_en = (row.get("main_category_en") or "").strip()
            if main_category_en.lower() == "wheat flours":
                main_category_en = "Wheat flours"

            updates[barcode] = ProductUpdate(
                product_name=(row.get("product_name") or "").strip(),
                brand=normalize_optional(row.get("brand")),
                main_category_en=main_category_en,
                allergens=normalize_optional(row.get("allergens")),
                category_tags=normalize_category_tags(row.get("category_tags") or ""),
                ingredients_text=normalize_optional(row.get("ingredients_text")),
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
    category_tags = update.category_tags
    categories = tags_to_categories(category_tags)
    main_category = resolve_main_category(update.main_category_en, category_tags)
    food_groups, food_groups_tags = resolve_food_groups(category_tags)

    values[FIELD_PRODUCT_NAME] = update.product_name
    values[FIELD_CATEGORIES] = categories
    values[FIELD_CATEGORY_TAGS] = category_tags
    values[FIELD_MAIN_CATEGORY] = main_category
    values[FIELD_MAIN_CATEGORY_EN] = update.main_category_en
    values[FIELD_FOOD_GROUPS] = food_groups
    values[FIELD_FOOD_GROUPS_TAGS] = food_groups_tags

    if update.brand is not None:
        values[FIELD_BRAND] = update.brand
    if update.ingredients_text is not None:
        values[FIELD_INGREDIENTS_TEXT] = update.ingredients_text
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
