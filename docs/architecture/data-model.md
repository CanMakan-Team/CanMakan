# Data model

Logical ER of **core** tables. Full DDL: [`00_schema.sql`](../../server/backend/src/main/resources/00_schema.sql).

Subscription / plan / feature-usage tables are seeded but have **no product API** (UC23). `ocr_scan_results` is schema-only (UC24).

```mermaid
erDiagram
  roles ||--o{ users : has
  users ||--o{ refresh_tokens : issues
  users ||--o{ family_members : joins
  users ||--o{ dietary_profiles : linked
  families ||--o{ family_members : contains
  families ||--o{ family_invitations : sends
  families ||--o{ dietary_profiles : owns
  dietary_profiles ||--o{ profile_restrictions : selects
  dietary_restrictions ||--o{ profile_restrictions : applied
  dietary_restrictions ||--o{ ingredient_restrictions : maps
  ingredients ||--o{ ingredient_restrictions : maps
  products ||--o{ product_ingredients : lists
  ingredients ||--o{ product_ingredients : in
  users ||--o{ scans : records
  dietary_profiles ||--o{ scans : against
  products ||--o{ scans : of
  scans ||--o{ ai_execution_logs : traces
  dietary_profiles ||--o{ recommendation_logs : for
  scans ||--o{ recommendation_logs : from
  products ||--o{ recommendation_logs : source_or_alt
```

| Cluster | Tables | Used by |
| --- | --- | --- |
| Auth | `roles`, `users`, `refresh_tokens` | [`auth`](../../server/backend/src/main/java/com/canmakan/backend/auth/README.md) |
| Family | `families`, `family_members`, `family_invitations` | [`family`](../../server/backend/src/main/java/com/canmakan/backend/family/README.md) |
| Diet | `dietary_profiles`, `dietary_restrictions`, `profile_restrictions` | [`dietaryprofile`](../../server/backend/src/main/java/com/canmakan/backend/dietaryprofile/README.md) |
| Catalog | `products`, `ingredients`, `product_ingredients` | scan + UC5 |
| Assess | `scans`, `ai_execution_logs`, `recommendation_logs` | [`product`](../../server/backend/src/main/java/com/canmakan/backend/product/README.md), [`ai`](../../server/backend/src/main/java/com/canmakan/backend/ai/README.md) |
