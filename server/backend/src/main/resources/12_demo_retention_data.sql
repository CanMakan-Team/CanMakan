-- =============================================================================
-- 12_demo_retention_data.sql
-- Demo data so UC15 "Retention & churn" shows real numbers.
--
-- Why the base seed shows 0: all base users have created_at = NOW(), so nobody
-- is old enough to be in any retention cohort, and all activity is recent (no
-- churn / no long-gap resurrection). This file adds back-dated users (ids 14-20)
-- whose registration dates and follow-up scans are spread over time.
--
-- Retention is period-independent (fixed 1/7/30 days from now). Churn and
-- resurrection are computed against the selected period; these are tuned for the
-- default 7-day view. Ids use a high 9101+ range for scans to avoid collisions
-- (base scans <= 50, demo scans 9001-9010 in 11_*.sql). New user ids 14-20 are
-- free (base users are 1-13). All timestamps are relative to NOW().
--
-- Retention cohort (all created 40-80 days ago), retained = a scan at/after
-- created_at + N days:
--   D1  retained: 14,15,16,17,18,20  (6/7 ~= 86%)
--   D7  retained: 14,15,16,17,20     (5/7 ~= 71%)
--   D30 retained: 14,15,20           (3/7 ~= 43%)
--   19 = registration-day scan only -> in cohort, never retained
--   20 = also resurrected (old scan 70 days ago + a recent scan)
--   15 = also churned at the 7-day view (active in the prior week, not this week)
-- =============================================================================

-- Back-dated app users (role_id = 2). Password hash reused from 04_roles_users.sql.
INSERT INTO users (id, role_id, email, password_hash, is_active, created_at, updated_at) VALUES
(14, 2, 'demo14@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(15, 2, 'demo15@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(16, 2, 'demo16@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(17, 2, 'demo17@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(18, 2, 'demo18@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(19, 2, 'demo19@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 40 DAY),
(20, 2, 'demo20@example.com', '$2a$10$oTcHsQELTJSku.uoOLnwYu4s5ACfDft4d/fTKxlxWHnMDyxwijWWO', 1, NOW() - INTERVAL 80 DAY, NOW() - INTERVAL 80 DAY);

-- Follow-up scans (profile 1 and barcode 95500539 already exist; FK only needs
-- the profile/barcode to exist, not to belong to the scanning user).
INSERT INTO scans (id, user_id, profile_id, barcode, verdict, ai_explanation, findings_json, scanned_at) VALUES
(9101, 14, 1, '95500539', 'SAFE', 'Return visit scan.',          '[]', NOW() - INTERVAL 3 DAY),
(9102, 15, 1, '95500539', 'SAFE', 'Active last week only.',       '[]', NOW() - INTERVAL 8 DAY),
(9103, 16, 1, '95500539', 'SAFE', 'Retained at day 7.',           '[]', NOW() - INTERVAL 15 DAY),
(9104, 17, 1, '95500539', 'SAFE', 'Retained at day 7.',           '[]', NOW() - INTERVAL 20 DAY),
(9105, 18, 1, '95500539', 'SAFE', 'Retained at day 1 only.',      '[]', NOW() - INTERVAL 36 DAY),
(9106, 19, 1, '95500539', 'SAFE', 'Registration-day scan only.',  '[]', NOW() - INTERVAL 40 DAY),
(9107, 20, 1, '95500539', 'SAFE', 'Old scan before the gap.',     '[]', NOW() - INTERVAL 70 DAY),
(9108, 20, 1, '95500539', 'SAFE', 'Returned after a long gap.',   '[]', NOW() - INTERVAL 3 DAY);
