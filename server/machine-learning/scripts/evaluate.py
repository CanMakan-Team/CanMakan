#!/usr/bin/env python3
"""Evaluate TF-IDF artifact stats and labeled substitute ranking pairs."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from canmakan_ml.domain import ProfileHints
from canmakan_ml.ranker import ProductTfidfModel, rank_products


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
        "--ranker",
        default="",
        help="Optional tfidf_ranker.joblib for end-to-end rank evaluation",
    )
    parser.add_argument(
        "--products",
        default="artifacts/products.json",
        help="Catalog products JSON (required with --ranker)",
    )
    parser.add_argument(
        "--pairs",
        default=str(default_pairs_path()),
        help="Labeled good/bad substitute pairs JSON",
    )
    args = parser.parse_args()

    pairs_path = Path(args.pairs)
    if not pairs_path.is_file():
        print(f"\nNo labeled pairs file at {pairs_path}")
        return

    pairs = json.loads(pairs_path.read_text(encoding="utf-8"))

    if args.ranker:
        ranker_path = Path(args.ranker)
        products_path = Path(args.products)
        if not ranker_path.is_file() or not products_path.is_file():
            raise SystemExit("Ranker evaluation requires --ranker and --products files")
        model = ProductTfidfModel.load(ranker_path)
        products = {item["barcode"]: item for item in json.loads(products_path.read_text(encoding="utf-8"))}
        failures = 0
        for case in pairs:
            source = products.get(case["source_barcode"])
            if source is None:
                print(f"MISSING source product {case['source_barcode']}")
                failures += 1
                continue
            candidates = [products[barcode] for barcode in case.get("good", []) + case.get("bad", []) if barcode in products]
            ranked = rank_products(model, source, candidates, ProfileHints())
            order = [item.barcode for item in ranked]
            best_good_index = min((order.index(barcode) for barcode in case.get("good", []) if barcode in order), default=-1)
            worst_bad_index = max((order.index(barcode) for barcode in case.get("bad", []) if barcode in order), default=-1)
            if best_good_index >= 0 and worst_bad_index >= 0 and best_good_index > worst_bad_index:
                print(f"FAIL rank order for {case.get('id')}")
                failures += 1
        print(f"\njoblib ranker labeled pairs: {failures} failure(s)")
        return

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
        failures = evaluate_pairs(products, pairs)
        if failures:
            print(f"\nlabeled pairs: {failures} diagnostic warning(s) (pre-filter cosine; rule engine still required)")
        else:
            print("\nlabeled pairs: ok")
    else:
        print(f"\nNo labeled pairs file at {pairs_path}")


if __name__ == "__main__":
    main()
