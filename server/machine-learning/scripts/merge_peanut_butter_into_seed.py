#!/usr/bin/env python3
"""Merge 01d peanut-butter overlay fields into 01_products.sql INSERT rows."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_products import (
    PRODUCT_COLUMNS,
    _split_sql_tuple,
    default_seed_path,
)

OVERLAY_COLUMNS = [
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

COLUMN_INDEX = {name: index for index, name in enumerate(PRODUCT_COLUMNS)}

UPDATE_BLOCK_PATTERN = re.compile(
    r"UPDATE products\s+SET\s+(.*?)\s+WHERE barcode = '([^']+)';",
    re.DOTALL,
)
ASSIGNMENT_PATTERN = re.compile(r"^\s*(\w+) = (.+?)(?:,\s*)?$", re.MULTILINE)
BARCODE_LINE_PATTERN = re.compile(r"^\('([^']+)'")
TUPLE_LINE_PATTERN = re.compile(r"^\((.*)\)[,;]?\s*$")


def parse_overlay_updates(overlay_path: Path) -> dict[str, dict[str, Any | None]]:
    updates: dict[str, dict[str, Any | None]] = {}
    text = overlay_path.read_text(encoding="utf-8")
    for match in UPDATE_BLOCK_PATTERN.finditer(text):
        assignments_text, barcode = match.group(1), match.group(2)
        fields: dict[str, Any | None] = {}
        for line in assignments_text.splitlines():
            assignment = ASSIGNMENT_PATTERN.match(line)
            if not assignment:
                continue
            column, raw_value = assignment.group(1), assignment.group(2).strip()
            if raw_value.upper() == "NULL":
                fields[column] = None
            else:
                fields[column] = raw_value[1:-1].replace("''", "'")
        updates[barcode] = fields
    return updates


def format_sql_value(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def format_row(values: list[Any]) -> str:
    return "(" + ", ".join(format_sql_value(value) for value in values) + ")"


def parse_tuple_line(line: str) -> list[Any] | None:
    stripped = line.strip().rstrip(",;")
    match = TUPLE_LINE_PATTERN.match(stripped)
    if not match:
        return None
    values = _split_sql_tuple(match.group(1))
    if len(values) != len(PRODUCT_COLUMNS):
        return None
    return values


def merge_overlay_into_seed(seed_path: Path, overlay_path: Path) -> int:
    overlay = parse_overlay_updates(overlay_path)
    lines = seed_path.read_text(encoding="utf-8").splitlines(keepends=True)
    updated = 0

    for index, line in enumerate(lines):
        barcode_match = BARCODE_LINE_PATTERN.match(line.strip())
        if not barcode_match:
            continue
        barcode = barcode_match.group(1)
        if barcode not in overlay:
            continue

        values = parse_tuple_line(line)
        if values is None:
            continue

        for column in OVERLAY_COLUMNS:
            values[COLUMN_INDEX[column]] = overlay[barcode].get(column)

        suffix = "," if line.strip().endswith(",") else ";"
        newline = "\n" if line.endswith("\n") else ""
        lines[index] = format_row(values) + suffix + newline
        updated += 1

    seed_path.write_text("".join(lines), encoding="utf-8")
    return updated


def main() -> None:
    repo_root = Path(__file__).resolve().parents[3]
    seed_path = default_seed_path()
    overlay_path = (
        repo_root
        / "server"
        / "backend"
        / "src"
        / "main"
        / "resources"
        / "01d_peanut_butter_product_updates.sql"
    )
    updated = merge_overlay_into_seed(seed_path, overlay_path)
    print(f"Merged overlay into {updated} rows in {seed_path}")


if __name__ == "__main__":
    main()
