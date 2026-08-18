# Runtime image for the UC5 FastAPI ranker (Python 3.12).
# CI trains artifacts/tfidf_ranker.joblib, then copies it in.
# Alpine avoids Debian perl-base / gzip / libacl image alerts; pip is not in the final image.
FROM python:3.12-alpine AS builder

WORKDIR /build

RUN apk add --no-cache build-base gfortran openblas-dev

COPY requirements-runtime.txt /build/requirements-runtime.txt
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir --prefix=/install -r /build/requirements-runtime.txt \
    && rm -rf /install/bin/pip* /install/bin/wheel /install/lib/python*/site-packages/pip* \
        /install/lib/python*/site-packages/setuptools* /install/lib/python*/site-packages/wheel*

FROM python:3.12-alpine

WORKDIR /app

RUN apk upgrade --no-cache \
    && addgroup -S canmakan \
    && adduser -S -G canmakan -H canmakan

COPY --from=builder /install /usr/local
COPY --chown=canmakan:canmakan src /app/src
COPY --chown=canmakan:canmakan artifacts/tfidf_ranker.joblib /app/artifacts/tfidf_ranker.joblib

ENV PYTHONPATH=/app/src
ENV ARTIFACT_PATH=/app/artifacts/tfidf_ranker.joblib

USER canmakan

EXPOSE 8091

CMD ["uvicorn", "canmakan_ml.api:app", "--host", "0.0.0.0", "--port", "8091"]
