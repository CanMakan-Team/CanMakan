#!/usr/bin/env python3
"""Print basic stats for the Tier C TF-IDF artifact."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate product_feature_vectors.json")
    parser.add_argument(
        "--artifact",
        default="artifacts/product_feature_vectors.json",
        help="Path to product_feature_vectors.json",
    )
    args = parser.parse_args()

    path = Path(args.artifact)
    if not path.is_file():
        raise SystemExit(f"Artifact not found: {path}")

    data = json.loads(path.read_text(encoding="utf-8"))
    products = data.get("products", {})
    term_counts = [len(vector) for vector in products.values() if isinstance(vector, dict)]

    print(f"artifact: {path}")
    print(f"version: {data.get('version')}")
    print(f"product_count: {len(products)}")
    print(f"vocabulary_size: {data.get('vocabulary_size')}")
    if term_counts:
        print(f"avg_terms_per_product: {sum(term_counts) / len(term_counts):.1f}")
        print(f"max_terms_per_product: {max(term_counts)}")


if __name__ == "__main__":
    main()
