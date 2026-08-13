#!/usr/bin/env python3
"""Generate additive UPDATE SQL from the peanut-butter CSV overlay."""

from __future__ import annotations

import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_products import default_seed_path, parse_products_sql

COLUMNS = [
    "category_tags",
    "main_category_en",
    "ingredients_text",
    "allergens",
    "allergens_en",
    "traces_tags",
    "traces_en",
    "labels_tags",
    "labels_en",
]


def sql_value(raw: str | None) -> str:
    if raw is None:
        return "NULL"
    value = raw.strip()
    if value == "" or value.upper() == "NULL":
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Generate peanut-butter overlay UPDATE SQL from CSV.")
    parser.add_argument("--csv", type=Path, required=True)
    parser.add_argument(
        "--sql-output",
        type=Path,
        default=Path(__file__).resolve().parents[3]
        / "server"
        / "backend"
        / "src"
        / "main"
        / "resources"
        / "01d_peanut_butter_product_updates.sql",
    )
    args = parser.parse_args()

    seed_path = default_seed_path()
    catalog = {str(row["barcode"]): row for row in parse_products_sql(seed_path.read_text(encoding="utf-8"))}

    with args.csv.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))

    missing = [row["barcode"] for row in rows if row["barcode"] not in catalog]
    statements: list[str] = [
        "-- Overlay peanut-butter / spread catalog fields from csv_Update_peanut_butter_product.csv.",
        "-- Additive UPDATEs on existing products.barcode rows. Not a new table.",
        f"-- CSV rows: {len(rows)}. Matched: {len(rows) - len(missing)}. Missing: {len(missing)}.",
        "",
    ]
    if missing:
        statements.append("-- Missing barcodes (not updated): " + ", ".join(missing))
        statements.append("")

    updated = 0
    for row in rows:
        barcode = row["barcode"].strip()
        if barcode not in catalog:
            continue
        assignments = [f"    {column} = {sql_value(row.get(column))}" for column in COLUMNS]
        statements.append("UPDATE products")
        statements.append("SET")
        statements.append(",\n".join(assignments))
        statements.append(f"WHERE barcode = {sql_value(barcode)};")
        statements.append("")
        updated += 1

    args.sql_output.write_text("\n".join(statements), encoding="utf-8")
    print(f"Wrote {updated} UPDATE statements to {args.sql_output}")
    if missing:
        print(f"Skipped missing barcodes: {missing}")


if __name__ == "__main__":
    main()
