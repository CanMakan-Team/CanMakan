"""Tests for ranker train/save/load."""

from canmakan_ml.ranker import ProductTfidfModel, rank_products
from canmakan_ml.domain import ProfileHints


def test_train_save_load_round_trip(tiny_products, tiny_ranker_path) -> None:
    loaded = ProductTfidfModel.load(tiny_ranker_path)
    source = tiny_products[0]
    ranked = rank_products(loaded, source, tiny_products[1:3], ProfileHints())
    assert len(ranked) == 2
    assert ranked[0].score >= ranked[1].score


def test_rank_returns_match_reason(tiny_products, tiny_ranker) -> None:
    ranked = rank_products(
        tiny_ranker,
        tiny_products[0],
        tiny_products[1:2],
        ProfileHints(),
    )
    assert ranked[0].match_reason
