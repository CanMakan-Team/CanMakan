# integration

External system adapters.

## Purpose
Isolates all communication with third-party services.

## Typical contents
- Open Food Facts client
- OpenRouter / OpenAI client
- Any future external providers

## Rules
- No business logic
- Only HTTP clients, request/response mapping, and error translation
- Configuration for these clients lives in `common/config`