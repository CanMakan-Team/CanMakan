#!/usr/bin/env python3
"""
Infer missing product metadata from the seeded catalog (01_products.sql).

Safe inference only (offline ETL):
  - main_category_en from product-name keyword rules
  - main_category_en from brand-level mode (same brand, complete rows)
  - main_category_en from sibling SKU (same brand, similar name)

Does NOT infer ingredients, allergens, or dietary labels.

Outputs:
  - 09_inferred_fields.sql (MySQL patch for Spring Boot seed)
  - optional inference report CSV

Usage:
  python infer_product_fields.py

  python infer_product_fields.py --apply-to-seed --copy-to-resources
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import sys
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path

DEFAULT_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)
"""Note: input actual local paths to run """
DEFAULT_OUTPUT = (
    r"C:/Users/.../09_inferred_fields.sql"
)
DEFAULT_RULES = Path(__file__).resolve().parent / "rules" / "category_keywords.json"
DEFAULT_REPORT = (
    Path(__file__).resolve().parent / "output" / "inferred_fields_report.csv"
)

# Column order in 01_products.sql INSERT tuples
IDX_BARCODE = 0
IDX_PRODUCT_NAME = 1
IDX_BRAND = 3
IDX_MAIN_CATEGORY = 9
IDX_MAIN_CATEGORY_EN = 10
IDX_INGREDIENTS_TEXT = 13

BATCH_SIZE = 500

INSERT_HEADER = (
    "INSERT INTO products (barcode, product_name, main_category_en) VALUES\n"
)
INSERT_FOOTER = (
    "\nON DUPLICATE KEY UPDATE "
    "main_category_en = IFNULL(main_category_en, VALUES(main_category_en));\n\n"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Infer missing main_category_en for products in 01_products.sql."
    )
    parser.add_argument(
        "--products-sql",
        default=str(DEFAULT_PRODUCTS_SQL),
        help="Source products seed SQL file",
    )
    parser.add_argument(
        "--output",
        default=DEFAULT_OUTPUT,
        help="Generated SQL patch path",
    )
    parser.add_argument(
        "--rules",
        default=str(DEFAULT_RULES),
        help="JSON file with [{pattern, category}, ...] keyword rules",
    )
    parser.add_argument(
        "--report",
        default=str(DEFAULT_REPORT),
        help="CSV report of inferred rows",
    )
    parser.add_argument(
        "--brand-min-count",
        type=int,
        default=2,
        help="Minimum complete rows per brand for brand-mode inference (default: 2)",
    )
    parser.add_argument(
        "--sibling-similarity",
        type=float,
        default=0.85,
        help="Minimum name similarity for sibling SKU inference (default: 0.85)",
    )
    parser.add_argument(
        "--copy-to-resources",
        action="store_true",
        help="Also write patch to src/main/resources/09_inferred_fields.sql",
    )
    parser.add_argument(
        "--apply-to-seed",
        action="store_true",
        help="Apply inferred main_category_en directly into 01_products.sql",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="Skip .bak backup when --apply-to-seed is used",
    )
    return parser.parse_args()


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
            if not token:
                raise ValueError(f"Unexpected empty token near position {start}")
            values.append(token)

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


def is_blank(value: str | None) -> bool:
    return value is None or not str(value).strip()


def normalize_brand(brand: str | None) -> str:
    if not brand:
        return ""
    return re.sub(r"\s+", " ", brand.strip().lower())


def normalize_name(name: str | None) -> str:
    if not name:
        return ""
    cleaned = re.sub(r"[^\w\s]", " ", name.lower())
    return re.sub(r"\s+", " ", cleaned).strip()


def load_category_rules(rules_path: Path) -> list[tuple[re.Pattern[str], str]]:
    with rules_path.open("r", encoding="utf-8") as handle:
        raw_rules = json.load(handle)

    compiled: list[tuple[re.Pattern[str], str]] = []
    for rule in raw_rules:
        pattern = rule.get("pattern")
        category = rule.get("category")
        if not pattern or not category:
            continue
        compiled.append((re.compile(pattern, re.IGNORECASE), category))
    return compiled


def load_products(products_sql_path: Path) -> tuple[list[dict], list[str]]:
    products: list[dict] = []
    raw_lines: list[str] = []
    tuple_line_pattern = re.compile(r"^\('([^']+)'")

    with products_sql_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            raw_lines.append(line)
            match = tuple_line_pattern.match(line.strip())
            if not match:
                continue

            body = extract_tuple_body(line)
            if body is None:
                continue

            try:
                values = parse_sql_tuple_values(body)
            except ValueError:
                continue

            if len(values) <= IDX_MAIN_CATEGORY_EN:
                continue

            products.append(
                {
                    "barcode": values[IDX_BARCODE] or "",
                    "product_name": values[IDX_PRODUCT_NAME],
                    "brand": values[IDX_BRAND],
                    "main_category": values[IDX_MAIN_CATEGORY],
                    "main_category_en": values[IDX_MAIN_CATEGORY_EN],
                    "ingredients_text": values[IDX_INGREDIENTS_TEXT],
                    "values": values,
                    "line_index": len(raw_lines) - 1,
                }
            )

    return products, raw_lines


def build_brand_category_map(
    products: list[dict],
    min_count: int,
) -> dict[str, str]:
    brand_categories: dict[str, list[str]] = {}

    for product in products:
        category = product["main_category_en"]
        brand_key = normalize_brand(product["brand"])
        if not brand_key or is_blank(category):
            continue
        brand_categories.setdefault(brand_key, []).append(category.strip())

    brand_mode: dict[str, str] = {}
    for brand_key, categories in brand_categories.items():
        if len(categories) < min_count:
            continue
        counter = Counter(categories)
        brand_mode[brand_key] = counter.most_common(1)[0][0]

    return brand_mode


def build_complete_by_brand(products: list[dict]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = {}
    for product in products:
        if is_blank(product["main_category_en"]):
            continue
        brand_key = normalize_brand(product["brand"])
        if not brand_key:
            continue
        grouped.setdefault(brand_key, []).append(product)
    return grouped


def infer_from_name(
    product_name: str | None,
    rules: list[tuple[re.Pattern[str], str]],
) -> str | None:
    if is_blank(product_name):
        return None
    haystack = product_name.lower()
    for pattern, category in rules:
        if pattern.search(haystack):
            return category
    return None


def infer_from_brand(
    brand: str | None,
    brand_mode: dict[str, str],
) -> str | None:
    brand_key = normalize_brand(brand)
    if not brand_key:
        return None
    return brand_mode.get(brand_key)


def infer_from_sibling(
    product_name: str | None,
    brand: str | None,
    complete_by_brand: dict[str, list[dict]],
    min_similarity: float,
) -> str | None:
    brand_key = normalize_brand(brand)
    target_name = normalize_name(product_name)
    if not brand_key or not target_name:
        return None

    candidates = complete_by_brand.get(brand_key, [])
    best_score = 0.0
    best_category: str | None = None

    for candidate in candidates:
        candidate_name = normalize_name(candidate["product_name"])
        if not candidate_name:
            continue
        score = SequenceMatcher(None, target_name, candidate_name).ratio()
        if score >= min_similarity and score > best_score:
            best_score = score
            best_category = candidate["main_category_en"]

    return best_category


def infer_products(
    products: list[dict],
    rules: list[tuple[re.Pattern[str], str]],
    brand_mode: dict[str, str],
    complete_by_brand: dict[str, list[dict]],
    sibling_similarity: float,
) -> list[dict]:
    inferred_rows: list[dict] = []

    for product in products:
        if not is_blank(product["main_category_en"]):
            continue

        barcode = product["barcode"]
        product_name = product["product_name"]
        brand = product["brand"]

        category: str | None = None
        source: str | None = None

        category = infer_from_name(product_name, rules)
        if category:
            source = "NAME_RULE"
        else:
            category = infer_from_brand(brand, brand_mode)
            if category:
                source = "BRAND_MODE"
            else:
                category = infer_from_sibling(
                    product_name,
                    brand,
                    complete_by_brand,
                    sibling_similarity,
                )
                if category:
                    source = "SIBLING_SKU"

        if not category:
            continue

        inferred_rows.append(
            {
                "barcode": barcode,
                "product_name": product_name or "",
                "brand": brand or "",
                "main_category_en": category,
                "inference_source": source,
                "had_ingredients_text": not is_blank(product["ingredients_text"]),
            }
        )

    return inferred_rows


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    trimmed = value.strip()
    if not trimmed:
        return "NULL"
    escaped = trimmed.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def write_sql_patch(output_path: Path, inferred_rows: list[dict]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as writer:
        writer.write("-- Generated by infer_product_fields.py\n")
        writer.write("-- Safe inference: main_category_en only (NULL fields).\n\n")

        batch: list[str] = []
        for row in inferred_rows:
            tuple_sql = (
                f"({sql_string(row['barcode'])}, "
                f"{sql_string(row['product_name'])}, "
                f"{sql_string(row['main_category_en'])})"
            )
            batch.append(tuple_sql)

            if len(batch) >= BATCH_SIZE:
                writer.write(INSERT_HEADER)
                writer.write(",\n".join(batch))
                writer.write(INSERT_FOOTER)
                batch.clear()

        if batch:
            writer.write(INSERT_HEADER)
            writer.write(",\n".join(batch))
            writer.write(INSERT_FOOTER)


def write_report(report_path: Path, inferred_rows: list[dict]) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    columns = [
        "barcode",
        "product_name",
        "brand",
        "main_category_en",
        "inference_source",
        "had_ingredients_text",
    ]

    with report_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for row in inferred_rows:
            writer.writerow({column: row[column] for column in columns})


NUMERIC_FIELD_INDICES = {6, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40}


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


def apply_inferences_to_seed(
    products: list[dict],
    inferred_by_barcode: dict[str, dict],
    raw_lines: list[str],
    products_sql_path: Path,
) -> int:
    updated = 0

    for product in products:
        barcode = product["barcode"]
        inferred = inferred_by_barcode.get(barcode)
        if not inferred:
            continue
        if not is_blank(product["main_category_en"]):
            continue

        values = list(product["values"])
        values[IDX_MAIN_CATEGORY_EN] = inferred["main_category_en"]
        if is_blank(values[IDX_MAIN_CATEGORY]):
            values[IDX_MAIN_CATEGORY] = inferred["main_category_en"]

        rebuilt = format_sql_tuple(values)
        line_index = product["line_index"]
        original = raw_lines[line_index]
        if original.strip().endswith("),"):
            raw_lines[line_index] = rebuilt + ",\n"
        else:
            raw_lines[line_index] = rebuilt + ";\n"
        updated += 1

    products_sql_path.write_text("".join(raw_lines), encoding="utf-8")
    return updated


def main() -> int:
    args = parse_args()
    products_sql_path = Path(args.products_sql)
    output_path = Path(args.output)
    rules_path = Path(args.rules)
    report_path = Path(args.report)

    if not products_sql_path.is_file():
        print(f"ERROR: products SQL not found: {products_sql_path}", file=sys.stderr)
        return 1
    if not rules_path.is_file():
        print(f"ERROR: rules file not found: {rules_path}", file=sys.stderr)
        return 1

    rules = load_category_rules(rules_path)
    products, raw_lines = load_products(products_sql_path)
    print(f"Loaded {len(products)} products from {products_sql_path}")

    brand_mode = build_brand_category_map(products, args.brand_min_count)
    complete_by_brand = build_complete_by_brand(products)
    print(f"Brand-mode map: {len(brand_mode)} brands")

    inferred_rows = infer_products(
        products=products,
        rules=rules,
        brand_mode=brand_mode,
        complete_by_brand=complete_by_brand,
        sibling_similarity=args.sibling_similarity,
    )

    source_counts = Counter(row["inference_source"] for row in inferred_rows)
    missing_category_before = sum(
        1 for product in products if is_blank(product["main_category_en"])
    )

    write_sql_patch(output_path, inferred_rows)
    write_report(report_path, inferred_rows)

    if args.copy_to_resources:
        resources_path = (
            Path(__file__).resolve().parent.parent
            / "src/main/resources/09_inferred_fields.sql"
        )
        resources_path.write_text(output_path.read_text(encoding="utf-8"), encoding="utf-8")
        print(f"Copied patch to: {resources_path}")

    if args.apply_to_seed:
        if not args.no_backup:
            backup_path = products_sql_path.with_suffix(products_sql_path.suffix + ".bak")
            shutil.copy2(products_sql_path, backup_path)
            print(f"Backup created: {backup_path}")

        inferred_by_barcode = {row["barcode"]: row for row in inferred_rows}
        applied = apply_inferences_to_seed(
            products,
            inferred_by_barcode,
            raw_lines,
            products_sql_path,
        )
        print(f"Applied {applied} inferred categories into {products_sql_path}")

    print(f"Wrote SQL patch: {output_path}")
    print(f"Wrote report: {report_path}")
    print("Summary:")
    print(f"  products_total: {len(products)}")
    print(f"  missing_main_category_en_before: {missing_category_before}")
    print(f"  inferred_rows: {len(inferred_rows)}")
    for source, count in sorted(source_counts.items()):
        print(f"  source_{source.lower()}: {count}")
    print(
        f"  still_missing_main_category_en: "
        f"{missing_category_before - len(inferred_rows)}"
    )

    if inferred_rows:
        sample = inferred_rows[0]
        print(
            "  sample:",
            f"{sample['barcode']} | {sample['inference_source']} | "
            f"{sample['main_category_en']} | {sample['product_name'][:50]}",
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
