#!/usr/bin/env python3
"""Evaluate TF-IDF artifact stats and labeled substitute ranking pairs."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


def cosine(left: dict[str, float], right: dict[str, float]) -> float:
    if not left or not right:
        return 0.0
    dot = sum(weight * right[term] for term, weight in left.items() if term in right)
    left_norm = math.sqrt(sum(weight * weight for weight in left.values()))
    right_norm = math.sqrt(sum(weight * weight for weight in right.values()))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def default_pairs_path() -> Path:
    return Path(__file__).resolve().parent.parent / "artifacts" / "labeled_substitute_pairs.json"


def evaluate_pairs(products: dict[str, dict[str, float]], pairs: list[dict]) -> int:
    failures = 0
    for case in pairs:
        source = case["source_barcode"]
        source_vec = products.get(source, {})
        print(f"\ncase: {case.get('id')} source={source}")
        if not source_vec:
            print("  MISSING source vector")
            failures += 1
            continue
        good_scores = []
        for barcode in case.get("good", []):
            score = cosine(source_vec, products.get(barcode, {}))
            present = barcode in products
            print(f"  good {barcode} present={present} cosine={score:.4f}")
            if not present:
                failures += 1
            good_scores.append(score)
        for barcode in case.get("bad", []):
            score = cosine(source_vec, products.get(barcode, {}))
            print(f"  bad  {barcode} present={barcode in products} cosine={score:.4f}")
        if good_scores and case.get("bad"):
            best_good = max(good_scores)
            worst_bad = max(
                (cosine(source_vec, products.get(barcode, {})) for barcode in case["bad"]),
                default=0.0,
            )
            if best_good < worst_bad:
                print("  FAIL: a bad barcode scored above the best good barcode (pre-filter cosine only)")
                failures += 1
    return failures


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate product_feature_vectors.json")
    parser.add_argument(
        "--artifact",
        default="artifacts/product_feature_vectors.json",
        help="Path to product_feature_vectors.json",
    )
    parser.add_argument(
        "--pairs",
        default=str(default_pairs_path()),
        help="Labeled good/bad substitute pairs JSON",
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

    pairs_path = Path(args.pairs)
    if pairs_path.is_file():
        pairs = json.loads(pairs_path.read_text(encoding="utf-8"))
        if failures:
            print(f"\nlabeled pairs: {failures} diagnostic warning(s) (pre-filter cosine; rule engine still required)")
        else:
            print("\nlabeled pairs: ok")
    else:
        print(f"\nNo labeled pairs file at {pairs_path}")


if __name__ == "__main__":
    main()
