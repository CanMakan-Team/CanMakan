# shared/exception

Shared API exception mapping.

## Purpose

Cross-cutting `@RestControllerAdvice` handlers. Feature packages keep their own exception types.

## Contents

| File | Role |
| --- | --- |
| [`AuthenticatedUserNotFoundException.java`](AuthenticatedUserNotFoundException.java) | Authenticated caller missing from `users` |
| [`GlobalExceptionHandler.java`](GlobalExceptionHandler.java) | Maps that case to HTTP 401 |
| [`ProductLookupExceptionHandler.java`](ProductLookupExceptionHandler.java) | OFF / product lookup failures |
