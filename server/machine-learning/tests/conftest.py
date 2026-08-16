"""Shared pytest fixtures."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from canmakan_ml.ranker import ProductTfidfModel

FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture(scope="session")
def tiny_products() -> list[dict]:
    return json.loads((FIXTURES / "products_tiny.json").read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def tiny_ranker(tiny_products: list[dict]) -> ProductTfidfModel:
    return ProductTfidfModel(max_features=500).fit(tiny_products)


@pytest.fixture(scope="session")
def tiny_ranker_path(tmp_path_factory, tiny_products: list[dict]) -> Path:
    path = tmp_path_factory.mktemp("ranker") / "tfidf_ranker.joblib"
    model = ProductTfidfModel(max_features=500).fit(tiny_products)
    model.save(path)
    return path
