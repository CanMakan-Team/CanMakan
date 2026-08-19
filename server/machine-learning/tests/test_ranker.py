"""Tests for ranker train/save/load."""

from scipy import sparse

from canmakan_ml.ranker import ProductTfidfModel, cosine_sparse, rank_products
from canmakan_ml.domain import ProfileHints


def test_cosine_sparse_zero_vector_is_zero() -> None:
    left = sparse.csr_matrix([[1.0, 0.0]])
    right = sparse.csr_matrix([[0.0, 0.0]])
    assert cosine_sparse(left, right) == 0.0


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
