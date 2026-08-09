#!/usr/bin/env python3
"""
Export the top N incomplete Singapore catalog products ranked by scan popularity.

"Incomplete" means at least one of:
  - ingredients_text is missing
  - main_category_en is missing
  - completeness < threshold (default 0.5)

Data sources:
  - Barcode allow-list from 01_products.sql (your seeded catalog)
  - Product + popularity fields from the OFF CSV export

Usage:
  python export_top_incomplete.py

  python export_top_incomplete.py --limit 100 --output ../output/top_100_incomplete.csv
"""

from __future__ import annotations

import argparse
import csv
import gzip
import re
import sys
from pathlib import Path

csv.field_size_limit(sys.maxsize)

DEFAULT_INPUT = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/"
    r"en.openfoodfacts.org.products.csv.gz"
)
DEFAULT_OUTPUT = (
    r"C:/Users/ChaiLee/OneDrive - National University of Singapore/AD/top_500_incomplete.csv"
)
DEFAULT_EXISTING_PRODUCTS_SQL = (
    Path(__file__).resolve().parent.parent / "src/main/resources/01_products.sql"
)

OUTPUT_COLUMNS = [
    "rank",
    "barcode",
    "product_name",
    "brand",
    "completeness",
    "unique_scans_n",
    "popularity_tags",
    "main_category_en",
    "has_ingredients_text",
    "incomplete_reasons",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export top incomplete products ranked by unique_scans_n."
    )
    parser.add_argument("--input", default=DEFAULT_INPUT, help="OFF CSV .gz path")
    parser.add_argument(
        "--output",
        default=DEFAULT_OUTPUT,
        help="Output CSV path (default: top_500_incomplete.csv in AD folder)",
    )
    parser.add_argument(
        "--existing-products-sql",
        default=str(DEFAULT_EXISTING_PRODUCTS_SQL),
        help="Seed file used to restrict barcodes to your catalog",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=500,
        help="Number of rows to export (default: 500)",
    )
    parser.add_argument(
        "--completeness-threshold",
        type=float,
        default=0.5,
        help="Rows with completeness below this are incomplete (default: 0.5)",
    )
    parser.add_argument(
        "--include-zero-scans",
        action="store_true",
        help="Include incomplete products even when unique_scans_n is empty",
    )
    parser.add_argument(
        "--copy-to-scripts-output",
        action="store_true",
        help="Also write to server/backend/scripts/output/top_N_incomplete.csv",
    )
    return parser.parse_args()


def load_existing_barcodes(products_sql_path: Path) -> set[str]:
    barcode_pattern = re.compile(r"^\('([^']+)'")
    barcodes: set[str] = set()

    with products_sql_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line.startswith("('"):
                continue
            match = barcode_pattern.match(line)
            if match:
                barcodes.add(match.group(1))

    if not barcodes:
        raise ValueError(f"No barcodes parsed from {products_sql_path}")

    return barcodes


def is_singapore_row(countries_tags: str | None) -> bool:
    countries = (countries_tags or "").lower()
    return "singapore" in countries or "en:singapore" in countries


def parse_float(value: str | None) -> float | None:
    if value is None:
        return None
    trimmed = value.strip()
    if not trimmed:
        return None
    try:
        return float(trimmed)
    except ValueError:
        return None


def parse_int(value: str | None) -> int | None:
    if value is None:
        return None
    trimmed = value.strip()
    if not trimmed:
        return None
    try:
        return int(float(trimmed))
    except ValueError:
        return None


def is_blank(value: str | None) -> bool:
    return value is None or not value.strip() or value.strip() == "0"


def incomplete_reasons(
    ingredients_text: str | None,
    main_category_en: str | None,
    completeness: float | None,
    threshold: float,
) -> list[str]:
    reasons: list[str] = []
    if is_blank(ingredients_text):
        reasons.append("missing_ingredients_text")
    if is_blank(main_category_en):
        reasons.append("missing_main_category_en")
    if completeness is None or completeness < threshold:
        reasons.append(f"low_completeness(<{threshold})")
    return reasons


def sort_key(row: dict) -> tuple:
    scans = row["unique_scans_n"]
    completeness = row["completeness"]
    # Higher scans first; NULL scans sort last.
    scans_sort = scans if scans is not None else -1
    completeness_sort = completeness if completeness is not None else -1.0
    return (scans_sort, completeness_sort, row["product_name"] or "")


def collect_incomplete_rows(
    input_path: Path,
    allowed_barcodes: set[str],
    completeness_threshold: float,
    include_zero_scans: bool,
) -> list[dict]:
    incomplete_rows: list[dict] = []

    with gzip.open(input_path, "rt", encoding="utf-8", newline="") as gz_file:
        reader = csv.DictReader(gz_file, delimiter="\t")
        required = {
            "code",
            "product_name",
            "brands",
            "countries_tags",
            "main_category_en",
            "ingredients_text",
            "completeness",
            "popularity_tags",
            "unique_scans_n",
        }
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"CSV missing expected columns: {sorted(missing)}")

        for row in reader:
            if not is_singapore_row(row.get("countries_tags")):
                continue

            barcode = (row.get("code") or "").strip()
            if not barcode or barcode not in allowed_barcodes:
                continue

            completeness = parse_float(row.get("completeness"))
            reasons = incomplete_reasons(
                row.get("ingredients_text"),
                row.get("main_category_en"),
                completeness,
                completeness_threshold,
            )
            if not reasons:
                continue

            unique_scans_n = parse_int(row.get("unique_scans_n"))
            if not include_zero_scans and unique_scans_n is None:
                continue

            incomplete_rows.append(
                {
                    "barcode": barcode,
                    "product_name": (row.get("product_name") or "").strip(),
                    "brand": (row.get("brands") or "").strip(),
                    "completeness": completeness,
                    "unique_scans_n": unique_scans_n,
                    "popularity_tags": (row.get("popularity_tags") or "").strip(),
                    "main_category_en": (row.get("main_category_en") or "").strip(),
                    "has_ingredients_text": not is_blank(row.get("ingredients_text")),
                    "incomplete_reasons": ";".join(reasons),
                }
            )

    incomplete_rows.sort(key=sort_key, reverse=True)
    return incomplete_rows


def write_csv(rows: list[dict], output_path: Path, limit: int) -> int:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    selected = rows[:limit]

    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        for index, row in enumerate(selected, start=1):
            writer.writerow(
                {
                    "rank": index,
                    "barcode": row["barcode"],
                    "product_name": row["product_name"],
                    "brand": row["brand"],
                    "completeness": "" if row["completeness"] is None else row["completeness"],
                    "unique_scans_n": "" if row["unique_scans_n"] is None else row["unique_scans_n"],
                    "popularity_tags": row["popularity_tags"],
                    "main_category_en": row["main_category_en"],
                    "has_ingredients_text": row["has_ingredients_text"],
                    "incomplete_reasons": row["incomplete_reasons"],
                }
            )

    return len(selected)


def main() -> int:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output)
    existing_path = Path(args.existing_products_sql)

    if not input_path.is_file():
        print(f"ERROR: OFF export not found: {input_path}", file=sys.stderr)
        return 1
    if not existing_path.is_file():
        print(f"ERROR: Products seed not found: {existing_path}", file=sys.stderr)
        return 1

    allowed_barcodes = load_existing_barcodes(existing_path)
    print(f"Loaded {len(allowed_barcodes)} barcodes from {existing_path}")
    print(f"Reading OFF export: {input_path}")

    incomplete_rows = collect_incomplete_rows(
        input_path=input_path,
        allowed_barcodes=allowed_barcodes,
        completeness_threshold=args.completeness_threshold,
        include_zero_scans=args.include_zero_scans,
    )

    written = write_csv(incomplete_rows, output_path, args.limit)

    if args.copy_to_scripts_output:
        scripts_output = (
            Path(__file__).resolve().parent
            / "output"
            / f"top_{args.limit}_incomplete.csv"
        )
        scripts_output.parent.mkdir(parents=True, exist_ok=True)
        scripts_output.write_text(output_path.read_text(encoding="utf-8"), encoding="utf-8")
        print(f"Copied CSV to: {scripts_output}")

    print(f"Wrote {written} rows to: {output_path}")
    print("Summary:")
    print(f"  incomplete_candidates: {len(incomplete_rows)}")
    print(f"  exported: {written}")
    print(f"  completeness_threshold: {args.completeness_threshold}")
    print(f"  include_zero_scans: {args.include_zero_scans}")

    if incomplete_rows:
        top = incomplete_rows[0]
        print(
            "  top row:",
            f"{top['barcode']} | scans={top['unique_scans_n']} | {top['product_name'][:60]}",
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
