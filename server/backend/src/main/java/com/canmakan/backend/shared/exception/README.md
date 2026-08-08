# shared/exception

Global exception handling.

## Purpose
Defines shared exceptions and central error handling for the API.

## Contents
- `AuthenticatedUserNotFoundException` — caller id (e.g. `X-User-Id`) not found in `users`
- `GlobalExceptionHandler` — maps that to HTTP 401
- `ProductLookupExceptionHandler` — product lookup failures

## Typical contents
- Base exceptions (`BusinessException`, `NotFoundException`, etc.)
- `@ControllerAdvice` / `@RestControllerAdvice`
- Standard error response DTO
- Exception-to-HTTP status mapping
