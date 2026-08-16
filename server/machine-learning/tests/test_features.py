"""Tests for feature helpers."""

from canmakan_ml.features import is_placeholder_ingredients, normalize_tags


def test_normalize_tags_strips_en_prefix() -> None:
    assert "milk-substitutes" in normalize_tags("en:milk-substitutes,en:oat-based-drinks")


def test_placeholder_ingredients_detected() -> None:
    product = {
        "main_category_en": "Fresh milks",
        "ingredients_text": "Fresh milks",
    }
    assert is_placeholder_ingredients(product) is True


def test_real_ingredients_not_placeholder() -> None:
    product = {
        "main_category_en": "Brown Rice Flour",
        "ingredients_text": "Organic Brown Rice",
    }
    assert is_placeholder_ingredients(product) is False


def test_sklearn_import() -> None:
    import sklearn  # noqa: F401

    assert sklearn.__version__
