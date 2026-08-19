# admin

System administration APIs (platform `ADMIN` JWT only).

## Purpose

Account status, system health, usage/trends HTTP surface, and scan-feedback review.

## Package layout (large feature)

Nested like other large features: `dto/`, `exception/`, `model/`, `repository/`, `service/`, with [`AdminController.java`](AdminController.java) at the root.

## Responsibilities

| Area | Notes |
| --- | --- |
| User accounts | List/filter; suspend/reactivate ([`UserAccountManagementService`](service/UserAccountManagementService.java)). Role is a filter, not CRUD. |
| System health | [`SystemHealthService`](service/SystemHealthService.java) including AI execution log summaries |
| Analytics HTTP | Delegates to [`analytics`](../analytics/README.md) (`/consumer-trends`, `/usage-statistics`) |
| Scan feedback | [`AdminScanFeedbackService`](service/AdminScanFeedbackService.java) — `GET /api/admin/scan-feedback`, `PATCH .../resolved` |
| Subscriptions | Schema/seed only (`07_subscriptions_usage.sql`). No admin subscription API (UC23). |

All endpoints in this package are admin-role protected.
