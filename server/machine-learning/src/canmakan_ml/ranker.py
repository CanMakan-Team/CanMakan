"""TF-IDF ranker: fit, persist, and score catalog product candidates."""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer

from canmakan_ml.domain import ProfileHints, score_candidate
from canmakan_ml.features import (
    apply_query_downweight,
    field_text,
    product_matrix_inputs,
)


@dataclass
class RankedProduct:
    barcode: str
    score: float
    match_reason: str


class ProductTfidfModel:
    """Field-specific TF-IDF vectorizers fitted on the catalog."""

    def __init__(self, max_features: int = 5000) -> None:
        self.max_features = max_features
        self.name_char = TfidfVectorizer(
            analyzer="char_wb",
            ngram_range=(3, 5),
            max_features=max_features // 4,
            sublinear_tf=True,
        )
        self.name_word = TfidfVectorizer(
            analyzer="word",
            token_pattern=r"(?u)\b\w+\b",
            max_features=max_features // 4,
            sublinear_tf=True,
        )
        self.category = TfidfVectorizer(
            analyzer="word",
            token_pattern=r"(?u)\b\w+\b",
            max_features=max_features // 8,
            sublinear_tf=True,
        )
        self.brand = TfidfVectorizer(
            analyzer="word",
            token_pattern=r"(?u)\b\w+\b",
            max_features=max_features // 8,
            sublinear_tf=True,
        )
        self.category_tags = TfidfVectorizer(
            analyzer="word",
            token_pattern=r"(?u)\b\w+\b",
            max_features=max_features // 8,
            sublinear_tf=True,
        )
        self.labels_tags = TfidfVectorizer(
            analyzer="word",
            token_pattern=r"(?u)\b\w+\b",
            max_features=max_features // 8,
            sublinear_tf=True,
        )
        self._feature_names: list[str] | None = None

    def _split_inputs(self, products: list[dict[str, Any]]) -> tuple[list[str], ...]:
        rows = [product_matrix_inputs(product) for product in products]
        return tuple(list(column) for column in zip(*rows, strict=True))

    def fit(self, products: list[dict[str, Any]]) -> ProductTfidfModel:
        name_char, name_word, category, brand, category_tags, labels_tags = self._split_inputs(
            products
        )
        self.name_char.fit(name_char)
        self.name_word.fit(name_word)
        self.category.fit(category)
        self.brand.fit(brand)
        self.category_tags.fit(category_tags)
        self.labels_tags.fit(labels_tags)
        self._feature_names = self._build_feature_names()
        return self

    def _build_feature_names(self) -> list[str]:
        names: list[str] = []
        for vectorizer in (
            self.name_char,
            self.name_word,
            self.category,
            self.brand,
            self.category_tags,
            self.labels_tags,
        ):
            names.extend(vectorizer.get_feature_names_out().tolist())
        return names

    def transform(self, products: list[dict[str, Any]], query: bool = False) -> sparse.csr_matrix:
        name_char, name_word, category, brand, category_tags, labels_tags = self._split_inputs(
            products
        )
        parts = [
            self.name_char.transform(name_char),
            self.name_word.transform(name_word),
            self.category.transform(category),
            self.brand.transform(brand),
            self.category_tags.transform(category_tags),
            self.labels_tags.transform(labels_tags),
        ]
        matrix = sparse.hstack(parts, format="csr")
        if query and self._feature_names is not None:
            for row_index in range(matrix.shape[0]):
                matrix[row_index] = apply_query_downweight(matrix.getrow(row_index), self._feature_names)
        return matrix

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(self, path)

    @staticmethod
    def load(path: Path) -> ProductTfidfModel:
        return joblib.load(path)


def cosine_sparse(left: sparse.csr_matrix, right: sparse.csr_matrix) -> float:
    if left.shape[1] == 0 or right.shape[1] == 0:
        return 0.0
    dot = float(left.multiply(right).sum())
    left_norm = math.sqrt(float(left.multiply(left).sum()))
    right_norm = math.sqrt(float(right.multiply(right).sum()))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def rank_products(
    model: ProductTfidfModel,
    source: dict[str, Any],
    candidates: list[dict[str, Any]],
    profile: ProfileHints,
) -> list[RankedProduct]:
    if not candidates:
        return []
    source_matrix = model.transform([source], query=True)
    candidate_matrix = model.transform(candidates, query=False)
    ranked: list[RankedProduct] = []
    for index, candidate in enumerate(candidates):
        similarity = cosine_sparse(source_matrix, candidate_matrix.getrow(index))
        score, reason = score_candidate(similarity, source, candidate, profile)
        barcode = candidate.get("barcode")
        if not barcode:
            continue
        ranked.append(RankedProduct(barcode=str(barcode), score=round(score, 4), match_reason=reason))
    ranked.sort(key=lambda item: item.score, reverse=True)
    return ranked
