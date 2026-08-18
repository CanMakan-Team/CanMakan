# Runtime image for the UC5 FastAPI ranker (Python 3.12).
# CI trains artifacts/tfidf_ranker.joblib from 01_products.sql, then copies it in.
FROM python:3.12-slim-trixie

WORKDIR /app

# Pick up Debian security updates that already have a Fixed Version (e.g. util-linux).
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system canmakan && useradd --system --gid canmakan --create-home canmakan

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY --chown=canmakan:canmakan src /app/src
COPY --chown=canmakan:canmakan artifacts/tfidf_ranker.joblib /app/artifacts/tfidf_ranker.joblib

ENV PYTHONPATH=/app/src
ENV ARTIFACT_PATH=/app/artifacts/tfidf_ranker.joblib

USER canmakan

EXPOSE 8091

CMD ["uvicorn", "canmakan_ml.api:app", "--host", "0.0.0.0", "--port", "8091"]
