#!/usr/bin/env python3
"""
Build sparse TF-IDF feature vectors from exported catalog products.

Input: artifacts/products.json (from export_products.py)
Output: artifacts/product_feature_vectors.json
"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
except ImportError as exc:  # pragma: no cover - runtime guard for optional dependency
    raise SystemExit(
        "scikit-learn is required. Install with: pip install -r requirements.txt"
    ) from exc


def normalize_tags(raw: str | None) -> str:
    if not raw:
        return ""
    tags = []
    for tag in raw.split(","):
        cleaned = tag.strip().lower().replace(" ", "-")
        if cleaned.startswith("en:"):
            cleaned = cleaned[3:]
        if cleaned:
            tags.append(cleaned)
    return " ".join(tags)


def product_document(product: dict[str, Any]) -> str:
    parts = [
        product.get("product_name") or "",
        product.get("brand") or "",
        product.get("main_category_en") or "",
        normalize_tags(product.get("category_tags")),
        normalize_tags(product.get("labels_tags")),
    ]
    return " ".join(part for part in parts if part).lower()


def tokenize_like_java(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def main() -> None:
    parser = argparse.ArgumentParser(description="Build TF-IDF product feature vectors.")
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("artifacts/products.json"),
        help="products.json from export_products.py",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/product_feature_vectors.json"),
        help="Output artifact path",
    )
    parser.add_argument(
        "--max-features",
        type=int,
        default=5000,
        help="Maximum TF-IDF vocabulary size",
    )
    args = parser.parse_args()

    products: list[dict[str, Any]] = json.loads(args.input.read_text(encoding="utf-8"))
    if not products:
        raise SystemExit(f"No products found in {args.input}")

    documents = [tokenize_like_java(product_document(product)) for product in products]
    barcodes = [product["barcode"] for product in products]

    vectorizer = TfidfVectorizer(
        analyzer="word",
        token_pattern=r"(?u)\b\w+\b",
        max_features=args.max_features,
        sublinear_tf=True,
    )
    matrix = vectorizer.fit_transform(documents)
    vocabulary = vectorizer.get_feature_names_out()

    product_vectors: dict[str, dict[str, float]] = {}
    for row_index, barcode in enumerate(barcodes):
        row = matrix.getrow(row_index)
        indices = row.indices
        values = row.data
        sparse_vector = {
            vocabulary[col_index]: round(float(weight), 6)
            for col_index, weight in zip(indices, values)
            if weight > 0
        }
        product_vectors[barcode] = sparse_vector

    artifact = {
        "version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": str(args.input),
        "vectorizer": {
            "type": "tfidf",
            "max_features": args.max_features,
            "fields": ["product_name", "brand", "main_category_en", "category_tags", "labels_tags"],
        },
        "vocabulary_size": len(vocabulary),
        "product_count": len(product_vectors),
        "products": product_vectors,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(artifact, indent=2), encoding="utf-8")
    print(
        f"Wrote {len(product_vectors)} vectors "
        f"(vocabulary={len(vocabulary)}) to {args.output}"
    )


if __name__ == "__main__":
    main()
