# shared/exception

Global exception handling.

## Purpose
Defines the exception hierarchy and central error handling for the API.

## Typical contents
- Base exceptions (`BusinessException`, `NotFoundException`, etc.)
- `@ControllerAdvice` / `@RestControllerAdvice`
- Standard error response DTO
- Exception-to-HTTP status mapping