-- =============================================
-- RECOMMENDATION LOGS (UC5 shown alternatives + UC17 history seed)
-- Derived from WARNING/UNSAFE scans in 06_scans_and_ai_logs.sql.
-- Only SAFE alternatives that exist in products.sql are logged.
-- =============================================
INSERT INTO recommendation_logs (
    id, profile_id, scan_id, source_barcode, recommended_barcode,
    recommended_name, recommended_brand, discovery_tier, verification_tier,
    rank_score, match_reason, data_quality, verdict_at_recommendation,
    shown_to_user, created_at
) VALUES

-- --------------------------------------------------------------------
-- Profile 1: Sarah Tan (Gluten + Low Sugar) — scans 2, 3, 5
-- --------------------------------------------------------------------
(1, 1, 2, '0038527591039', '95500539',
 'Sardines in tomato sauce', 'Ayam Brand', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9100, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 8 DAY + INTERVAL 5 MINUTE),

(2, 1, 3, '9300698500181', '95500539',
 'Sardines in tomato sauce', 'Ayam Brand', 'TIER_B_LLM_DISCOVERY', 'TIER_1_RULES',
 0.8200, 'llm_suggested_verified', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 6 DAY + INTERVAL 5 MINUTE),

(3, 1, 5, '675747001018', '95500539',
 'Sardines in tomato sauce', 'Ayam Brand', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8800, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 1 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 2: Michael Tan (Low Fat + Low Sodium) — scans 7, 8, 10
-- --------------------------------------------------------------------
(4, 2, 7, '8850581172007', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9300, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 9 DAY + INTERVAL 5 MINUTE),
(5, 2, 7, '8850581172007', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8700, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 9 DAY + INTERVAL 6 MINUTE),

(6, 2, 8, '9313839005087', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9000, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 7 DAY + INTERVAL 5 MINUTE),
(7, 2, 8, '9313839005087', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8500, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 7 DAY + INTERVAL 6 MINUTE),

(8, 2, 10, '8888077103549', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9200, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 3: Emily Tan (Dairy, Peanut, Low Sugar) — scans 11, 12, 14, 15
-- --------------------------------------------------------------------
(9, 3, 11, '07321122', '9319530000239',
 'Australian Jerky Crocodile Soft', 'Billabong', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9500, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 14 DAY + INTERVAL 5 MINUTE),

(10, 3, 12, '8888077102092', '9319530000239',
 'Australian Jerky Crocodile Soft', 'Billabong', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9400, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 11 DAY + INTERVAL 5 MINUTE),

(11, 3, 14, '4710154012793', '9319530000239',
 'Australian Jerky Crocodile Soft', 'Billabong', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8600, 'prior_safe_scan', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 5 DAY + INTERVAL 5 MINUTE),

(12, 3, 15, '9315536220107', '9319530000239',
 'Australian Jerky Crocodile Soft', 'Billabong', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9100, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 2 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 4: David Lim (Halal + Low Trans Fat) — scans 16, 17, 19
-- --------------------------------------------------------------------
(13, 4, 16, '9317276000032', '8997035600041',
 'Pocari Sweat', 'Pocari Sweat', 'TIER_B_LLM_DISCOVERY', 'TIER_1_RULES',
 0.8400, 'llm_suggested_verified', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 15 DAY + INTERVAL 5 MINUTE),
(14, 4, 16, '9317276000032', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.7900, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 15 DAY + INTERVAL 6 MINUTE),

(15, 4, 17, '6916063230510', '8997035600041',
 'Pocari Sweat', 'Pocari Sweat', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9300, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 12 DAY + INTERVAL 5 MINUTE),
(16, 4, 17, '6916063230510', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8800, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 12 DAY + INTERVAL 6 MINUTE),

(17, 4, 19, '8888383208648', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9000, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 4 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 5: Jessica Lim (Halal + Low Fat + Low Sodium) — scans 21, 23, 24
-- --------------------------------------------------------------------
(18, 5, 21, '4901330300067', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9200, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 13 DAY + INTERVAL 5 MINUTE),
(19, 5, 21, '4901330300067', '8888196305817',
 'Pokka Premium Earl Grey Milk Tea', 'Pokka', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8500, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 13 DAY + INTERVAL 6 MINUTE),

(20, 5, 23, '9316434288572', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8800, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 7 DAY + INTERVAL 5 MINUTE),

(21, 5, 24, '8850581172007', '8888196305817',
 'Pokka Premium Earl Grey Milk Tea', 'Pokka', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8600, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 3 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 6: Daniel Lim (Shellfish + Halal) — scans 26, 27, 29
-- --------------------------------------------------------------------
(22, 6, 26, '8885014850160', '8888077103549',
 'Plain Crackers', 'Meiji', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9100, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 11 DAY + INTERVAL 5 MINUTE),
(23, 6, 26, '8885014850160', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8300, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 11 DAY + INTERVAL 6 MINUTE),

(24, 6, 27, '8888279640019', '8888077103549',
 'Plain Crackers', 'Meiji', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9400, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 9 DAY + INTERVAL 5 MINUTE),

(25, 6, 29, '8885014850160', '8888077103549',
 'Plain Crackers', 'Meiji', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9000, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 4 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 7: Amanda Lim (Halal + Low Sugar) — scans 32, 33, 35
-- --------------------------------------------------------------------
(26, 7, 32, '8801123600328', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9100, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 10 DAY + INTERVAL 5 MINUTE),
(27, 7, 32, '8801123600328', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8700, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 10 DAY + INTERVAL 6 MINUTE),

(28, 7, 33, '8888196305817', '9557305001368',
 'Logan Red Dates drink', 'Marigold', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8900, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 7 DAY + INTERVAL 5 MINUTE),

(29, 7, 35, '9556771000028', '8888440000048',
 'Pure milk', 'Cowhead', 'TIER_B_LLM_DISCOVERY', 'TIER_1_RULES',
 0.8100, 'llm_suggested_verified', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 1 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 8: James Wong (Egg, Vegetarian, Low Sodium) — scans 37, 38, 39
-- --------------------------------------------------------------------
(30, 8, 37, '8854651008845', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9300, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 9 DAY + INTERVAL 5 MINUTE),
(31, 8, 37, '8854651008845', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8600, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 9 DAY + INTERVAL 6 MINUTE),

(32, 8, 38, '8888383208648', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9200, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 7 DAY + INTERVAL 5 MINUTE),

(33, 8, 39, '8888077103549', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8800, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 4 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 9: Olivia Wong (Vegan + Low Trans Fat) — scans 42, 43, 44
-- --------------------------------------------------------------------
(34, 9, 42, '5000119120656', '4710154012793',
 'C&C Orange', 'C&C', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9000, 'prior_safe_scan', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 10 DAY + INTERVAL 5 MINUTE),
(35, 9, 42, '5000119120656', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8700, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 10 DAY + INTERVAL 6 MINUTE),

(36, 9, 43, '8888077102092', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.9100, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 8 DAY + INTERVAL 5 MINUTE),

(37, 9, 44, '9316434288671', '4978045600683',
 'White Miso Paste', 'Aoki Miso', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8900, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 3 DAY + INTERVAL 5 MINUTE),

-- --------------------------------------------------------------------
-- Profile 10: Robert Wong (Dairy + Low Sugar) — scans 46, 48, 49
-- --------------------------------------------------------------------
(38, 10, 46, '9557305000545', '4987176009913',
 'Vicks vapo naturals (lemon)', NULL, 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8500, 'prior_safe_scan', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 15 DAY + INTERVAL 5 MINUTE),
(39, 10, 46, '9557305000545', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8200, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 15 DAY + INTERVAL 6 MINUTE),

(40, 10, 48, '4710154012793', '4987176009913',
 'Vicks vapo naturals (lemon)', NULL, 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8300, 'prior_safe_scan', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 6 DAY + INTERVAL 5 MINUTE),

(41, 10, 49, '8888440000048', '4987176009913',
 'Vicks vapo naturals (lemon)', NULL, 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.8000, 'prior_safe_scan', 'PARTIAL', 'SAFE', 1, NOW() - INTERVAL 3 DAY + INTERVAL 5 MINUTE),
(42, 10, 49, '8888440000048', '9311983909800',
 'Cayenne Pepper', 'Whittingtons', 'TIER_A_CATALOG', 'TIER_1_RULES',
 0.7800, 'prior_safe_scan', 'VERIFIED', 'SAFE', 1, NOW() - INTERVAL 3 DAY + INTERVAL 6 MINUTE);

-- =============================================
-- RECOMMENDATION AI LOGS (Tier B LLM discovery audit seed)
-- Pairs with scans where discovery_tier = TIER_B_LLM_DISCOVERY above.
-- =============================================
INSERT INTO recommendation_ai_logs (
    id, scan_id, profile_id, source_barcode, execution_tier,
    model_id, prompt_tokens, completion_tokens, latency_ms,
    llm_candidates_json, candidates_accepted, candidates_rejected, created_at
) VALUES
(1, 3, 1, '9300698500181', 'TIER_B_LLM_DISCOVERY',
 'gpt-4o-mini', 380, 95, 1420,
 '{"candidates":[{"name":"Sardines in tomato sauce","brand":"Ayam Brand","barcode":"95500539"},{"name":"C&C Orange","brand":"C&C","barcode":"4710154012793"}]}',
 1, 1, NOW() - INTERVAL 6 DAY + INTERVAL 4 MINUTE),

(2, 16, 4, '9317276000032', 'TIER_B_LLM_DISCOVERY',
 'gpt-4o-mini', 420, 110, 1580,
 '{"candidates":[{"name":"Pocari Sweat","brand":"Pocari Sweat","barcode":"8997035600041"},{"name":"Pure milk","brand":"Cowhead","barcode":"8888440000048"},{"name":"Brooklyn Defender IPA","brand":"Brooklyn Defender","barcode":"6916063230510"}]}',
 2, 1, NOW() - INTERVAL 15 DAY + INTERVAL 4 MINUTE),

(3, 35, 7, '9556771000028', 'TIER_B_LLM_DISCOVERY',
 'gpt-4o-mini', 395, 88, 1310,
 '{"candidates":[{"name":"Pure milk","brand":"Cowhead","barcode":"8888440000048"},{"name":"Logan Red Dates drink","brand":"Marigold","barcode":"9557305001368"}]}',
 1, 1, NOW() - INTERVAL 1 DAY + INTERVAL 4 MINUTE);
