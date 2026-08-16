"""FastAPI rank endpoint tests."""

import os

from fastapi.testclient import TestClient

from canmakan_ml.api import app, load_model


def test_health() -> None:
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_rank_endpoint(tiny_ranker_path, tiny_products, monkeypatch) -> None:
    monkeypatch.setenv("ARTIFACT_PATH", str(tiny_ranker_path))
    load_model.cache_clear()
    client = TestClient(app)
    source = tiny_products[0]
    candidates = tiny_products[1:4]
    response = client.post(
        "/rank",
        json={
            "source": source,
            "candidates": candidates,
            "profile": {
                "flour_substitute_discovery": True,
                "secondary_include_tags": ["en:brown-rice-flour", "en:amaranth-flour"],
                "include_tags": ["en:gluten-free-flour"],
            },
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert len(body["ranked"]) == 3
    assert body["ranked"][0]["barcode"]
    load_model.cache_clear()
    if "ARTIFACT_PATH" in os.environ:
        del os.environ["ARTIFACT_PATH"]
