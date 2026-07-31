-- =============================================
--  SCANS (Product Evaluation Events)
-- =============================================
INSERT INTO scans (id, user_id, profile_id, barcode, verdict, ai_explanation, findings_json, scanned_at) VALUES
(1, 4, 1, '8997035600041', 'SAFE', 
   'This product contains no gluten ingredients or wheat derivatives.', 
   '{"matched_rules": [], "allergens_found": []}', 
   NOW()),

(2, 4, 3, '7321122', 'UNSAFE', 
   'Contains peanuts which violates the user severe peanut allergy constraint.', 
   '{"matched_rules": ["PEANUT_ALLERGY"], "allergens_found": ["Peanuts"]}', 
   NOW()),

(3, 7, 4, '8888279300128', 'WARNING', 
   'Product is not Halal certified but does not explicitly list non-halal ingredients.', 
   '{"matched_rules": ["HALAL_UNCERTAIN"], "warnings": ["Missing Halal Certification"]}', 
   NOW()),

(4, 9, 6, '8885014850160', 'UNSAFE', 
   'Contains shrimp extract, which poses a severe risk due to shellfish allergy.', 
   '{"matched_rules": ["SHELLFISH_ALLERGY"], "allergens_found": ["Shrimp Extract"]}', 
   NOW()),

(5, 11, 8, '8888440000048', 'SAFE', 
   '100% Plant-based vegetarian product with no egg or animal derivatives.', 
   '{"matched_rules": [], "allergens_found": []}', 
   NOW());

-- =============================================
--  AI EXECUTION LOGS (Audit & Diagnostic Trail)
-- =============================================
INSERT INTO ai_execution_logs (id, scan_id, execution_tier, model_id, prompt_tokens, completion_tokens, latency_ms, compiled_prompt, raw_llm_response, created_at) VALUES
-- Deterministic Rule Engine Scan (Fast path, no LLM required)
(1, 1, 'TIER_1_RULES', NULL, NULL, NULL, 45, 
   '{"rule_engine": "deterministic_v1", "profile_id": 1}', 
   '{"status": "PASSED"}', 
   NOW()),

-- Deterministic Rule Engine Scan (Flagged allergen directly)
(2, 2, 'TIER_1_RULES', NULL, NULL, NULL, 38, 
   '{"rule_engine": "deterministic_v1", "profile_id": 3}', 
   '{"status": "FLAGGED", "trigger": "PEANUT"}', 
   NOW()),

-- Complex LLM Evaluation (Escalated to Tier 3 for deeper ingredient reasoning)
(3, 3, 'TIER_3_LLM', 'gpt-4o', 420, 115, 1250, 
   '{"system": "You are a Halal food analyst.", "user": "Analyze ingredient list for Halal compliance..."}', 
   '{"verdict": "WARNING", "reason": "Uncertified emulsifier E471"}', 
   NOW()),

-- Deterministic Rule Engine Scan
(4, 4, 'TIER_1_RULES', NULL, NULL, NULL, 52, 
   '{"rule_engine": "deterministic_v1", "profile_id": 6}', 
   '{"status": "FLAGGED", "trigger": "SHELLFISH"}', 
   NOW()),

-- Complex LLM Evaluation
(5, 5, 'TIER_3_LLM', 'gpt-4o', 380, 98, 980, 
   '{"system": "You are a dietary analyst.", "user": "Evaluate if product is suitable for Vegetarian and Egg allergy."}', 
   '{"verdict": "SAFE", "reason": "No animal or egg ingredients found"}', 
   NOW());