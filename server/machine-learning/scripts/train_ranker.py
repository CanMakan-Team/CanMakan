#!/usr/bin/env python3
"""Fit field-weighted TF-IDF ranker and persist as joblib."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from canmakan_ml.ranker import ProductTfidfModel


def main() -> None:
    parser = argparse.ArgumentParser(description="Train UC5 TF-IDF ranker.")
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("artifacts/products.json"),
        help="products.json from export_products.py",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/tfidf_ranker.joblib"),
        help="Output joblib artifact path",
    )
    parser.add_argument("--max-features", type=int, default=5000)
    args = parser.parse_args()

    products: list[dict] = json.loads(args.input.read_text(encoding="utf-8"))
    if not products:
        raise SystemExit(f"No products found in {args.input}")

    model = ProductTfidfModel(max_features=args.max_features).fit(products)
    model.save(args.output)
    print(f"Wrote ranker for {len(products)} products to {args.output}")


if __name__ == "__main__":
    main()
