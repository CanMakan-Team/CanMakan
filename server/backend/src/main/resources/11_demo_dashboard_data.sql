-- =============================================================================
-- 11_demo_dashboard_data.sql
-- Demo data so the admin dashboards show real numbers:
--   * UC15 Usage Statistics  -> user_sessions (Avg session / Sessions per user / Active days)
--   * UC16 System Health      -> recent scans + ai_execution_logs (24h window) and admin_audit_logs
-- All timestamps are relative to NOW() so the rows always land inside the
-- default look-back windows. The base seed (06_*.sql) uses scan ids up to 50 and
-- ai_execution_logs ids up to 5, so demo rows use a high 9001+ range to avoid any
-- primary-key collision. The schema is dropped/recreated on every startup, so
-- fixed ids never collide across restarts.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Recent scans (last 24h) -> feeds System Health "Scan data quality"
-- Mix of SAFE / WARNING / UNSAFE, plus one INCOMPLETE_DATA finding.
-- Reuses existing users (4), profiles (1,2) and product barcodes.
-- -----------------------------------------------------------------------------
INSERT INTO scans (id, user_id, profile_id, barcode, verdict, ai_explanation, findings_json, scanned_at) VALUES
(9001, 4, 1, '95500539',     'SAFE',    'No gluten ingredients detected.',                 '[]',                                                                                                              NOW() - INTERVAL 1 HOUR),
(9002, 4, 1, '9300698500181','WARNING', 'High sugar content (18g/100g).',                  '[{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"High sugar content (18g/100g)."}]',    NOW() - INTERVAL 3 HOUR),
(9003, 4, 2, '8850581172007','WARNING', 'High sodium content (850mg/100g).',               '[{"restrictionCode":"HIGH_SODIUM_WARNING","ingredientName":"Sodium","reason":"High sodium content (850mg/100g)."}]', NOW() - INTERVAL 5 HOUR),
(9004, 4, 1, '0038527591039','UNSAFE',  'Contains wheat flour (gluten).',                  '[{"restrictionCode":"GLUTEN_ALLERGY","ingredientName":"Wheat Flour","reason":"Contains wheat flour (gluten)."}]',  NOW() - INTERVAL 8 HOUR),
(9005, 4, 1, '4710154012793','SAFE',    'Gluten-free certified oats.',                     '[]',                                                                                                              NOW() - INTERVAL 11 HOUR),
(9006, 4, 2, '9557305001368','SAFE',    'Low fat and low sodium.',                         '[]',                                                                                                              NOW() - INTERVAL 14 HOUR),
(9007, 4, 1, '675747001018', 'UNSAFE',  'Contains barley malt extract (gluten).',          '[{"restrictionCode":"GLUTEN_ALLERGY","ingredientName":"Barley Malt Extract","reason":"Contains barley malt extract (gluten)."}]', NOW() - INTERVAL 18 HOUR),
(9008, 4, 1, NULL,           'WARNING', 'Ingredient list unavailable; cannot fully verify.','[{"restrictionCode":"INCOMPLETE_DATA","ingredientName":"Unlisted","reason":"Ingredient list unavailable."}]',     NOW() - INTERVAL 20 HOUR);

-- -----------------------------------------------------------------------------
-- Recent AI execution logs (last 24h) -> feeds System Health "AI execution
-- monitoring": Tier-3 share, avg/max latency, latency trend, slowest calls.
-- References the recent scans above (and two older base-seed scans, 3 and 10).
-- -----------------------------------------------------------------------------
INSERT INTO ai_execution_logs (id, scan_id, execution_tier, model_id, prompt_tokens, completion_tokens, latency_ms, compiled_prompt, raw_llm_response, created_at) VALUES
(9001, 9001, 'TIER_1_RULES', NULL,    NULL, NULL, 40,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"PASSED"}',                       NOW() - INTERVAL 1 HOUR),
(9002, 9002, 'TIER_3_LLM',   'gpt-4o', 380, 100,  1450, '{"user":"Evaluate sugar threshold compliance."}',             '{"verdict":"WARNING","reason":"Sugar exceeds limit."}', NOW() - INTERVAL 3 HOUR),
(9003, 9003, 'TIER_1_RULES', NULL,    NULL, NULL, 35,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"FLAGGED","trigger":"HIGH_SODIUM"}', NOW() - INTERVAL 5 HOUR),
(9004, 9004, 'TIER_1_RULES', NULL,    NULL, NULL, 28,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"FLAGGED","trigger":"GLUTEN_ALLERGY"}', NOW() - INTERVAL 8 HOUR),
(9005, 9005, 'TIER_3_LLM',   'gpt-4o', 420, 115,  1980, '{"user":"Confirm gluten-free certification."}',               '{"verdict":"SAFE","reason":"Certified gluten-free."}', NOW() - INTERVAL 11 HOUR),
(9006, 9006, 'TIER_1_RULES', NULL,    NULL, NULL, 44,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"PASSED"}',                       NOW() - INTERVAL 14 HOUR),
(9007, 9007, 'TIER_1_RULES', NULL,    NULL, NULL, 31,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"FLAGGED","trigger":"GLUTEN_ALLERGY"}', NOW() - INTERVAL 17 HOUR),
(9008, 9008, 'TIER_3_LLM',   'gpt-4o', 500, 140,  2380, '{"user":"Assess product with incomplete ingredient list."}',  '{"verdict":"WARNING","reason":"Insufficient data."}', NOW() - INTERVAL 20 HOUR),
(9009, 3,    'TIER_3_LLM',   'gpt-4o', 410, 110,  1180, '{"user":"Evaluate additives compliance."}',                   '{"verdict":"WARNING","reason":"Additive uncertain."}', NOW() - INTERVAL 22 HOUR),
(9010, 10,   'TIER_1_RULES', NULL,    NULL, NULL, 52,   '{"rule_engine":"deterministic_v1"}',                          '{"status":"FLAGGED"}',                      NOW() - INTERVAL 2 HOUR);

-- -----------------------------------------------------------------------------
-- User sessions (last 6 days) -> feeds UC15 engagement:
-- Avg session length, Sessions per user, Active days per week.
-- 14 sessions across 5 app users (ids 4-8). id is auto-increment.
-- -----------------------------------------------------------------------------
INSERT INTO user_sessions (user_id, started_at, last_heartbeat_at) VALUES
(4, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 2 HOUR + INTERVAL 300 SECOND),
(4, NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 1 DAY  + INTERVAL 600 SECOND),
(4, NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 2 DAY  + INTERVAL 180 SECOND),
(4, NOW() - INTERVAL 4 DAY,  NOW() - INTERVAL 4 DAY  + INTERVAL 900 SECOND),
(5, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 240 SECOND),
(5, NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 1 DAY  + INTERVAL 480 SECOND),
(5, NOW() - INTERVAL 3 DAY,  NOW() - INTERVAL 3 DAY  + INTERVAL 720 SECOND),
(6, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 5 HOUR + INTERVAL 360 SECOND),
(6, NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 2 DAY  + INTERVAL 200 SECOND),
(7, NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 1 DAY  + INTERVAL 150 SECOND),
(7, NOW() - INTERVAL 3 DAY,  NOW() - INTERVAL 3 DAY  + INTERVAL 900 SECOND),
(7, NOW() - INTERVAL 5 DAY,  NOW() - INTERVAL 5 DAY  + INTERVAL 300 SECOND),
(8, NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 4 HOUR + INTERVAL 600 SECOND),
(8, NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 1 DAY  + INTERVAL 420 SECOND);

-- -----------------------------------------------------------------------------
-- Admin audit trail (last few days) -> feeds System Health "Admin activity".
-- Actions by the three seeded admins (ids 1,2,3). id is auto-increment.
-- -----------------------------------------------------------------------------
INSERT INTO admin_audit_logs (admin_user_id, action_performed, target_entity, details, ip_address, created_at) VALUES
(1, 'LOGIN',                   'session',        '{"method":"password"}',                    '203.0.113.11', NOW() - INTERVAL 1 HOUR),
(1, 'VIEW_USAGE_STATISTICS',   'analytics',      '{"periodDays":30}',                        '203.0.113.11', NOW() - INTERVAL 1 HOUR),
(1, 'VIEW_SYSTEM_HEALTH',      'system',         '{"windowHours":24}',                       '203.0.113.11', NOW() - INTERVAL 2 HOUR),
(2, 'SUSPEND_USER',            'user:9',         '{"reason":"policy violation"}',            '198.51.100.24',NOW() - INTERVAL 4 HOUR),
(2, 'REACTIVATE_USER',         'user:9',         '{"reason":"appeal approved"}',             '198.51.100.24',NOW() - INTERVAL 3 HOUR),
(3, 'UPDATE_PRODUCT',          'product:95500539','{"field":"category"}',                    '203.0.113.77', NOW() - INTERVAL 6 HOUR),
(3, 'DELETE_SCAN_FEEDBACK',    'feedback:5',     '{"reason":"spam"}',                        '203.0.113.77', NOW() - INTERVAL 8 HOUR),
(1, 'EXPORT_CONSUMER_TRENDS',  'analytics',      '{"format":"csv"}',                         '203.0.113.11', NOW() - INTERVAL 1 DAY),
(2, 'VIEW_USER_ACCOUNTS',      'user',           '{"filter":"active"}',                      '198.51.100.24',NOW() - INTERVAL 1 DAY),
(3, 'LOGIN',                   'session',        '{"method":"password"}',                    '203.0.113.77', NOW() - INTERVAL 2 DAY),
(1, 'SUSPEND_USER',            'user:12',        '{"reason":"suspicious activity"}',         '203.0.113.11', NOW() - INTERVAL 2 DAY),
(2, 'VIEW_SYSTEM_HEALTH',      'system',         '{"windowHours":168}',                      '198.51.100.24',NOW() - INTERVAL 3 DAY);
