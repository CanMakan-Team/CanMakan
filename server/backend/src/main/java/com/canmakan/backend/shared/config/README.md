# shared/config

Spring Boot configuration classes.

## Purpose
Central place for all application configuration.

## Typical contents
- `SecurityConfig`
- `OpenAiConfig` / `OpenRouterConfig`
- `OpenFoodFactsConfig`
- `JacksonConfig`, `AsyncConfig`, `CacheConfig`, etc.
- Property binding classes (`@ConfigurationProperties`)

## Rules
- No business logic
- Only configuration and bean definitions