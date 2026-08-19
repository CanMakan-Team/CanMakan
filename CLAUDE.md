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

## 1. Think Before Coding

Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.
If multiple interpretations exist, present them - don't pick silently.
If a simpler approach exists, say so. Push back when warranted.
If something is unclear, stop. Name what's confusing. Ask. 2. Simplicity First
Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.
No abstractions for single-use code.
No "flexibility" or "configurability" that wasn't requested.
No error handling for impossible scenarios.
If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.
Don't refactor things that aren't broken.
Match existing style, even if you'd do it differently.
If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.
Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"
"Fix the bug" → "Write a test that reproduces it, then make it pass"
"Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
   Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
