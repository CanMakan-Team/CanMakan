"""Gold-set ranking tests aligned with UC5 demo barcodes."""

from canmakan_ml.domain import ProfileHints
from canmakan_ml.ranker import rank_products


def _by_barcode(products: list[dict], barcode: str) -> dict:
    return next(item for item in products if item["barcode"] == barcode)


def test_wheat_flour_prefers_brown_rice_in_top_five(tiny_products, tiny_ranker) -> None:
    source = _by_barcode(tiny_products, "9555064500016")
    candidates = [
        _by_barcode(tiny_products, barcode)
        for barcode in (
            "8887501030642",
            "8906055442630",
            "8887501030697",
            "8888030023662",
        )
    ]
    profile = ProfileHints(
        flour_substitute_discovery=True,
        secondary_include_tags=[
            "en:corn-starch",
            "en:brown-rice-flour",
            "en:buckwheat-flour",
            "en:amaranth-flour",
        ],
        include_tags=["en:gluten-free-flour"],
    )
    ranked = rank_products(tiny_ranker, source, candidates, profile)
    barcodes = [item.barcode for item in ranked[:5]]
    assert "8887501030642" in barcodes
    assert "8906055442630" in barcodes


def test_milk_prefers_unsweetened_soy(tiny_products, tiny_ranker) -> None:
    source = _by_barcode(tiny_products, "8888200602857")
    candidate = _by_barcode(tiny_products, "8850025000521")
    profile = ProfileHints(
        prefer_low_sugar=True,
        milk_substitute_discovery=True,
        secondary_include_tags=["en:unsweetened-plain-soy-based-drinks"],
        include_tags=["en:milk-substitutes"],
    )
    ranked = rank_products(tiny_ranker, source, [candidate], profile)
    assert ranked[0].barcode == "8850025000521"


def test_peanut_butter_prefers_nut_butter_over_jam(tiny_products, tiny_ranker) -> None:
    source = _by_barcode(tiny_products, "8888260007616")
    candidates = [
        _by_barcode(tiny_products, "95539553"),
        _by_barcode(tiny_products, "8888536703136"),
        _by_barcode(tiny_products, "0044936350150"),
    ]
    profile = ProfileHints(
        peanut_spread_discovery=True,
        include_tags=["en:nut-butters"],
        secondary_include_tags=["en:tahini", "en:cereal-butters"],
    )
    ranked = rank_products(tiny_ranker, source, candidates, profile)
    top_two = {item.barcode for item in ranked[:2]}
    assert "0044936350150" not in top_two
