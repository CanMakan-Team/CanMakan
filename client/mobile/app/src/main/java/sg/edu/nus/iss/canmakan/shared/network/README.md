# shared/network

Networking layer.

## Contains
- Retrofit setup
- OkHttp client & interceptors (auth, logging, retry, long-read timeout)
- API result wrappers
- Network-related error mapping

Default OkHttp read timeout is ~15s. `LongReadTimeoutInterceptor` raises it to 60s
for `POST /scan/assess` and paths that end with `/recommendations`. Those two
Retrofit methods also send `X-CanMakan-No-Retry: true` so `RetryPolicyInterceptor`
does not repeat a long backend call.

Actual API service interfaces can live here or be generated from OpenAPI.