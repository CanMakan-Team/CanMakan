-- =============================================
--  SUBSCRIPTION PLANS
-- =============================================
-- price_cents uses small-unit integers (999 for $9.99, 9900 for $99.00)
INSERT INTO subscription_plans (id, plan_code, name, price_cents, billing_period) VALUES
(1, 'FREE_BASE', 'Free Tier', 0, 'MONTHLY'),
(2, 'PREMIUM_FAMILY_MONTHLY', 'Premium Family Monthly', 999, 'MONTHLY'),
(3, 'PREMIUM_FAMILY_YEARLY', 'Premium Family Yearly', 9900, 'YEARLY');

-- =============================================
--  SUBSCRIPTIONS (Linked 1:1 with Families)
-- =============================================
INSERT INTO subscriptions (id, family_id, plan_id, status, expires_at, created_at) VALUES
-- Tan Family (Free Tier)
(1, 1, 1, 'ACTIVE', NULL, NOW()),

-- Lim Family (Premium Monthly)
(2, 2, 2, 'ACTIVE', DATE_ADD(NOW(), INTERVAL 1 MONTH), NOW()),

-- Wong Family (Premium Yearly)
(3, 3, 3, 'ACTIVE', DATE_ADD(NOW(), INTERVAL 1 YEAR), NOW());

-- =============================================
--  FEATURES
-- =============================================
INSERT INTO features (id, feature_code, description) VALUES
(1, 'MONTHLY_SCAN_LIMIT', 'Maximum number of barcode/ingredient scans allowed per month.'),
(2, 'FAMILY_PROFILES_LIMIT', 'Maximum number of dietary profiles permitted under the family account.'),
(3, 'AI_DEEP_ANALYSIS', 'Access to Tier-3 LLM deep ingredient safety reasoning.');

-- =============================================
--  PLAN FEATURES (Junction Table for Plan Entitlements)
-- =============================================
INSERT INTO plan_features (plan_id, feature_id, limit_value) VALUES
-- Free Tier (Plan 1): 30 scans, 2 profiles, 0 LLM deep scans
(1, 1, 30),
(1, 2, 2),
(1, 3, 0),

-- Premium Family Monthly (Plan 2): Unlimited scans, 10 profiles, Unlimited LLM scans (-1 = Unlimited)
(2, 1, -1),
(2, 2, 10),
(2, 3, -1),

-- Premium Family Yearly (Plan 3): Unlimited scans, 10 profiles, Unlimited LLM scans (-1 = Unlimited)
(3, 1, -1),
(3, 2, 10),
(3, 3, -1);

-- =============================================
-- FEATURE USAGE (Active Usage Metering)
-- =============================================
INSERT INTO feature_usage (id, family_id, feature_id, current_usage, reset_at) VALUES
-- Tan Family Usage Tracking
(1, 1, 1, 12, DATE_ADD(NOW(), INTERVAL 18 DAY)),
(2, 1, 2, 3, DATE_ADD(NOW(), INTERVAL 18 DAY)),

-- Lim Family Usage Tracking
(3, 2, 1, 145, DATE_ADD(NOW(), INTERVAL 12 DAY)),
(4, 2, 2, 4, DATE_ADD(NOW(), INTERVAL 12 DAY)),

-- Wong Family Usage Tracking
(5, 3, 1, 88, DATE_ADD(NOW(), INTERVAL 25 DAY)),
(6, 3, 2, 3, DATE_ADD(NOW(), INTERVAL 25 DAY));