# Project Code Standards

This file defines code quality expectations for this project. Follow these standards when generating, editing, or reviewing code here.

## Stack
Java, Kotlin, Hilt, Spring Boot, SQL, Retrofit
(Trim or add frameworks as needed for this specific project.)

## Readability & Naming
- Use descriptive variable, method, and class names. Avoid abbreviations unless standard in the domain (e.g. DTO, API, ID).
- Naming conventions: `camelCase` for variables and methods, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants.
- Each method should have a single responsibility. Split methods that handle more than one clear task.

## Structure & Consistency
- Use constructor injection for Hilt and Spring Boot components.
- Follow the repository pattern for data access.
- Use DTOs at API boundaries rather than passing entities directly.
- Spring Boot: keep controller, service, and repository layers separated.
- Kotlin/Android: follow standard Jetpack Compose and ViewModel/StateFlow conventions unless a specific reason requires otherwise.
- Keep complexity at a beginner-to-intermediate level. Prefer clear, explicit code over terse or advanced syntax when both achieve the same result.

## Comments
- Comments should explain what a section of code does or why a decision was made, not restate the obvious.
- Keep comment tone plain and neutral, not casual.
- Write comments as descriptions, not instructions. Avoid phrasing like "you should" or "your data."

## Error Handling & Safety
- Handle likely failure cases explicitly: null values, empty results, failed network calls, SQL constraint violations.
- SQL: always use parameterized queries. Never concatenate user input directly into a query string.
- Retrofit: handle network failures and non-2xx responses rather than assuming every call succeeds.

## Explanations
- Before introducing a new concept or pattern in code, briefly explain what it does and why it is used.
- When more than one valid approach exists, note the trade-off briefly rather than picking one silently.

## Testing
- New logic should include or suggest a corresponding unit test where practical.
- Favor testing behavior (inputs and outputs) over internal implementation details.
