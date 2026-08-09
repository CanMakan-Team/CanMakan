#!/usr/bin/env python3
"""
Merge manual ingredient backfill from add-product.csv into 01_products.sql.

Updates the ingredients_text column (field index 13) for matching barcodes.
Creates a .bak backup before overwriting the seed file.

Usage:
  python merge_manual_ingredients.py

  python merge_manual_ingredients.py \\
    --csv "C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/add-product.csv" \\
    --products-sql "../src/main/resources/01_products.sql"
"""

from __future__ import annotations

import argparse
import csv
import re
import shutil
import sys
from pathlib import Path

DEFAULT_CSV = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/add-product.csv"
)
DEFAULT_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)

INGREDIENTS_TEXT_INDEX = 13

# Excel scientific notation loses precision; map damaged keys to known barcodes.
SCIENTIFIC_BARCODE_OVERRIDES = {
    "4892290000000": "4891028162520",
    "4901330000000": "4901330305840",
    "7613040000000": "7613036049399",
    "8851020000000": "8851028001812",
    "8888030000000": "8888030300381",
}


def normalize_barcode(raw_barcode: str) -> str:
    barcode = raw_barcode.strip()
    if re.fullmatch(r"\d+\.?\d*[eE][+-]?\d+", barcode):
        normalized = str(int(float(barcode)))
        return SCIENTIFIC_BARCODE_OVERRIDES.get(normalized, normalized)
    return barcode


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Merge add-product.csv ingredient_text into 01_products.sql."
    )
    parser.add_argument("--csv", default=DEFAULT_CSV, help="Manual backfill CSV path")
    parser.add_argument(
        "--products-sql",
        default=str(DEFAULT_PRODUCTS_SQL),
        help="Target products seed SQL file",
    )
    parser.add_argument(
        "--encoding",
        default="cp1252",
        help="CSV file encoding (default: cp1252 for Windows Excel exports)",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="Skip creating a .bak backup of products.sql",
    )
    return parser.parse_args()


def load_manual_ingredients(csv_path: Path, encoding: str) -> dict[str, str]:
    ingredients: dict[str, str] = {}
    with csv_path.open("r", encoding=encoding, newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or "barcode" not in reader.fieldnames:
            raise ValueError("CSV must contain a barcode column")
        text_col = "ingredient_text" if "ingredient_text" in reader.fieldnames else "ingredients_text"
        if text_col not in reader.fieldnames:
            raise ValueError("CSV must contain ingredient_text or ingredients_text column")

        for row in reader:
            barcode = normalize_barcode(row.get("barcode") or "")
            text = (row.get(text_col) or "").strip()
            if not barcode or not text:
                continue
            ingredients[barcode] = text
    return ingredients


def parse_sql_tuple_values(tuple_body: str) -> list[str | None]:
    """Parse comma-separated SQL literal list inside parentheses."""
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


def extract_tuple_line(line: str) -> tuple[str, str] | None:
    stripped = line.strip()
    if not stripped.startswith("('"):
        return None
    if not (stripped.endswith("),") or stripped.endswith(");")):
        return None

    suffix = stripped[-2:] if stripped.endswith("),") else stripped[-2:]
    body = stripped[1:-2] if stripped.endswith("),") else stripped[1:-2]
    return stripped, body


def update_products_sql(
    products_sql_path: Path,
    manual_ingredients: dict[str, str],
) -> tuple[dict[str, int], set[str]]:
    stats = {
        "tuple_lines": 0,
        "updated": 0,
        "already_filled_skipped": 0,
        "missing_in_seed": 0,
        "parse_errors": 0,
    }

    missing_barcodes = set(manual_ingredients.keys())
    output_lines: list[str] = []

    tuple_line_pattern = re.compile(r"^\('([^']+)'")

    with products_sql_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            match = tuple_line_pattern.match(line.strip())
            if not match:
                output_lines.append(line)
                continue

            stats["tuple_lines"] += 1
            barcode = match.group(1)
            if barcode not in manual_ingredients:
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

            if len(values) <= INGREDIENTS_TEXT_INDEX:
                stats["parse_errors"] += 1
                output_lines.append(line)
                continue

            existing = values[INGREDIENTS_TEXT_INDEX]
            new_text = manual_ingredients[barcode]
            if existing and existing.strip():
                stats["already_filled_skipped"] += 1
                output_lines.append(line)
                continue

            values[INGREDIENTS_TEXT_INDEX] = new_text
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
    args = parse_args()
    csv_path = Path(args.csv)
    products_sql_path = Path(args.products_sql)

    if not csv_path.is_file():
        print(f"ERROR: CSV not found: {csv_path}", file=sys.stderr)
        return 1
    if not products_sql_path.is_file():
        print(f"ERROR: products SQL not found: {products_sql_path}", file=sys.stderr)
        return 1

    manual_ingredients = load_manual_ingredients(csv_path, args.encoding)
    print(f"Loaded {len(manual_ingredients)} manual ingredient rows from {csv_path}")

    if not args.no_backup:
        backup_path = products_sql_path.with_suffix(products_sql_path.suffix + ".bak")
        shutil.copy2(products_sql_path, backup_path)
        print(f"Backup created: {backup_path}")

    stats, missing_barcodes = update_products_sql(products_sql_path, manual_ingredients)

    print(f"Updated file: {products_sql_path}")
    print("Summary:")
    for key, value in stats.items():
        print(f"  {key}: {value}")

    if missing_barcodes:
        sample = sorted(missing_barcodes)[:10]
        print("  missing barcode examples:", ", ".join(sample))
        if len(missing_barcodes) > 10:
            print(f"  ... and {len(missing_barcodes) - 10} more")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
