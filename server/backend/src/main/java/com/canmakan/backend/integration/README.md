# integration

Open Food Facts and EAN-Search HTTP adapters for barcode product lookup.

## Purpose

Isolates third-party product lookup. Validate tries Open Food Facts first, then EAN-Search. Assess uses the OFF snapshot via `ProductDataAdapter` (not EAN). OpenAI / Tier-3 LLM clients live in [`ai`](../ai/README.md), not here.

## Contents

| File | Role |
| --- | --- |
| [`BarcodeValidationClient.java`](BarcodeValidationClient.java) | OFF then EAN-Search (`@Value` URLs/timeouts) |
| [`OpenFoodFactsResponse.java`](OpenFoodFactsResponse.java) | Response mapping |
| [`ProductLookupException.java`](ProductLookupException.java) | Lookup failures |
| [`RetrySleeper.java`](RetrySleeper.java) | Retry delay |

HTTP beans: [`shared/config/HttpClientConfig.java`](../shared/config/HttpClientConfig.java). No business logic in this package.
