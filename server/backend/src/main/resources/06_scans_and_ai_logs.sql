-- =============================================
-- SCANS (Product Evaluation Events)
-- =============================================
INSERT INTO scans (id, user_id, profile_id, barcode, verdict, ai_explanation, findings_json, scanned_at) VALUES
<<<<<<< Updated upstream
(1, 4, 1, '8886001234567', 'SAFE', 
   'This product contains no gluten ingredients or wheat derivatives.', 
   '{"matched_rules": [], "allergens_found": []}', 
   NOW()),

(2, 4, 3, '8886001234568', 'UNSAFE', 
   'Contains peanuts which violates the user severe peanut allergy constraint.', 
   '{"matched_rules": ["PEANUT_ALLERGY"], "allergens_found": ["Peanuts"]}', 
   NOW()),

(3, 7, 4, '8886001234569', 'WARNING', 
   'Product is not Halal certified but does not explicitly list non-halal ingredients.', 
   '{"matched_rules": ["HALAL_UNCERTAIN"], "warnings": ["Missing Halal Certification"]}', 
   NOW()),

(4, 9, 6, '8886001234570', 'UNSAFE', 
   'Contains shrimp extract, which poses a severe risk due to shellfish allergy.', 
   '{"matched_rules": ["SHELLFISH_ALLERGY"], "allergens_found": ["Shrimp Extract"]}', 
   NOW()),

(5, 11, 8, '8886001234571', 'SAFE', 
   '100% Plant-based vegetarian product with no egg or animal derivatives.', 
   '{"matched_rules": [], "allergens_found": []}', 
   NOW());
=======

-- --------------------------------------------------------------------
-- Profile 1: Sarah Tan (user_id: 4) -> Gluten (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(1, 4, 1, '8886001234567', 'SAFE', 'This product contains no gluten ingredients or wheat derivatives.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 10 DAY),
(2, 4, 1, '8886001234572', 'UNSAFE', 'Contains wheat flour which violates the gluten-free constraint.', '{"matched_rules": ["GLUTEN_ALLERGY"], "allergens_found": ["Wheat Flour"]}', NOW() - INTERVAL 8 DAY),
(3, 4, 1, '8886001234573', 'WARNING', 'High sugar content detected (18g/100g), exceeding preferred low sugar threshold.', '{"matched_rules": ["HIGH_SUGAR_WARNING"], "warnings": ["Sugar exceeds 10g/100g limit"]}', NOW() - INTERVAL 6 DAY),
(4, 4, 1, '8886001234574', 'SAFE', 'Gluten-free certified oats with low sugar content (2g/100g).', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 4 DAY),
(5, 4, 1, '8886001234575', 'UNSAFE', 'Contains barley malt extract which contains gluten.', '{"matched_rules": ["GLUTEN_ALLERGY"], "allergens_found": ["Barley Malt Extract"]}', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 2: Michael Tan (user_id: 4) -> Low Fat (PREFERENCE), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(6, 4, 2, '8886001234576', 'SAFE', 'Product aligns with low fat (1.2g/100g) and low sodium (140mg/100g) preferences.', '{"matched_rules": [], "warnings": []}', NOW() - INTERVAL 12 DAY),
(7, 4, 2, '8886001234577', 'WARNING', 'High sodium content detected (850mg/100g).', '{"matched_rules": ["HIGH_SODIUM_WARNING"], "warnings": ["Sodium exceeds 400mg/100g limit"]}', NOW() - INTERVAL 9 DAY),
(8, 4, 2, '8886001234578', 'WARNING', 'High fat content detected (22g total fat/100g).', '{"matched_rules": ["HIGH_FAT_WARNING"], "warnings": ["Total fat exceeds 10g/100g limit"]}', NOW() - INTERVAL 7 DAY),
(9, 4, 2, '8886001234579', 'SAFE', 'Low sodium (120mg/100g) and low fat (1.5g/100g) steamed rice cake snack.', '{"matched_rules": [], "warnings": []}', NOW() - INTERVAL 3 DAY),
(10, 4, 2, '8886001234580', 'WARNING', 'Contains both elevated total fat (15g/100g) and high sodium (600mg/100g).', '{"matched_rules": ["HIGH_FAT_WARNING", "HIGH_SODIUM_WARNING"], "warnings": ["Fat & Sodium exceed preferred limits"]}', NOW()),

-- --------------------------------------------------------------------
-- Profile 3: Emily Tan (user_id: 4) -> Dairy (INTOLERANCE), Peanut (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(11, 4, 3, '8886001234568', 'UNSAFE', 'Contains peanuts which violates the user severe peanut allergy constraint.', '{"matched_rules": ["PEANUT_ALLERGY"], "allergens_found": ["Peanuts"]}', NOW() - INTERVAL 14 DAY),
(12, 4, 3, '8886001234581', 'UNSAFE', 'Contains milk solids and whey powder (Lactose/Dairy Intolerance).', '{"matched_rules": ["DAIRY_INTOLERANCE"], "allergens_found": ["Milk Solids", "Whey"]}', NOW() - INTERVAL 11 DAY),
(13, 4, 3, '8886001234582', 'SAFE', 'Dairy-free, peanut-free almond-based rice crackers with zero added sugar.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 8 DAY),
(14, 4, 3, '8886001234583', 'WARNING', 'No peanut or dairy allergens, but sugar levels are high (24g/100g).', '{"matched_rules": ["HIGH_SUGAR_WARNING"], "warnings": ["High Sugar"]}', NOW() - INTERVAL 5 DAY),
(15, 4, 3, '8886001234584', 'UNSAFE', 'Manufactured in a facility that processes peanuts and tree nuts.', '{"matched_rules": ["CROSS_CONTAMINATION_PEANUT"], "allergens_found": ["Trace Peanuts"]}', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 4: David Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Trans Fat (PREFERENCE)
-- --------------------------------------------------------------------
(16, 7, 4, '8886001234569', 'WARNING', 'Product is not Halal certified but does not explicitly list non-halal ingredients.', '{"matched_rules": ["HALAL_UNCERTAIN"], "warnings": ["Missing Halal Certification"]}', NOW() - INTERVAL 15 DAY),
(17, 7, 4, '8886001234585', 'UNSAFE', 'Contains pork gelatin and mirin (alcohol), strictly non-Halal.', '{"matched_rules": ["NON_HALAL_INGREDIENT"], "allergens_found": ["Pork Gelatin", "Mirin"]}', NOW() - INTERVAL 12 DAY),
(18, 7, 4, '8886001234586', 'SAFE', 'Certified Halal by MUIS Singapore and contains 0g trans fat.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 9 DAY),
(19, 7, 4, '8886001234587', 'WARNING', 'Certified Halal, but contains partially hydrogenated oil (high trans fat: 0.8g/100g).', '{"matched_rules": ["HIGH_TRANS_FAT_WARNING"], "warnings": ["Trans Fat exceeds 0.5g/100g limit"]}', NOW() - INTERVAL 4 DAY),
(20, 7, 4, '8886001234588', 'SAFE', 'Certified Halal organic coconut water, completely trans-fat free.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 5: Jessica Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Fat (PREFERENCE), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(21, 7, 5, '8886001234589', 'UNSAFE', 'Contains lard (pork fat), non-Halal.', '{"matched_rules": ["NON_HALAL_INGREDIENT"], "allergens_found": ["Lard"]}', NOW() - INTERVAL 13 DAY),
(22, 7, 5, '8886001234590', 'SAFE', 'MUIS Halal certified, low fat (1g/100g), and low sodium (180mg/100g).', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 10 DAY),
(23, 7, 5, '8886001234591', 'WARNING', 'Halal certified, but contains high total fat (18g/100g).', '{"matched_rules": ["HIGH_FAT_WARNING"], "warnings": ["High Fat"]}', NOW() - INTERVAL 7 DAY),
(24, 7, 5, '8886001234592', 'WARNING', 'Halal certified, but sodium level is high (920mg/100g).', '{"matched_rules": ["HIGH_SODIUM_WARNING"], "warnings": ["High Sodium"]}', NOW() - INTERVAL 3 DAY),
(25, 7, 5, '8886001234593', 'SAFE', 'Halal certified steamed chicken broth, low fat and reduced sodium.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 6: Daniel Lim (user_id: 7) -> Shellfish (STRICT_AVOID), Halal (STRICT_AVOID)
-- --------------------------------------------------------------------
(26, 7, 6, '8886001234570', 'UNSAFE', 'Contains shrimp extract, which poses a severe risk due to shellfish allergy.', '{"matched_rules": ["SHELLFISH_ALLERGY"], "allergens_found": ["Shrimp Extract"]}', NOW() - INTERVAL 11 DAY),
(27, 7, 6, '8886001234594', 'UNSAFE', 'Contains crab meat paste and non-Halal flavoring agent.', '{"matched_rules": ["SHELLFISH_ALLERGY", "NON_HALAL_INGREDIENT"], "allergens_found": ["Crab Paste"]}', NOW() - INTERVAL 9 DAY),
(28, 7, 6, '8886001234595', 'SAFE', 'Certified Halal potato chips with zero crustacean or shellfish processing.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 6 DAY),
(29, 7, 6, '8886001234596', 'UNSAFE', 'Contains oyster sauce (shellfish derivative).', '{"matched_rules": ["SHELLFISH_ALLERGY"], "allergens_found": ["Oyster Sauce"]}', NOW() - INTERVAL 4 DAY),
(30, 7, 6, '8886001234597', 'SAFE', 'Halal vegetable spring rolls free from shellfish and seafood.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 7: Amanda Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(31, 7, 7, '8886001234598', 'SAFE', 'Certified Halal low-sugar fruit juice (3g sugar/100ml).', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 14 DAY),
(32, 7, 7, '8886001234599', 'UNSAFE', 'Contains uncertified beef gelatin (non-Halal).', '{"matched_rules": ["NON_HALAL_INGREDIENT"], "allergens_found": ["Uncertified Gelatin"]}', NOW() - INTERVAL 10 DAY),
(33, 7, 7, '8886001234600', 'WARNING', 'Halal certified milk tea, but contains high added sugar (16g/100ml).', '{"matched_rules": ["HIGH_SUGAR_WARNING"], "warnings": ["High Sugar"]}', NOW() - INTERVAL 7 DAY),
(34, 7, 7, '8886001234601', 'SAFE', 'MUIS Halal yogurt with zero added cane sugar.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 5 DAY),
(35, 7, 7, '8886001234602', 'WARNING', 'Uncertain Halal status and high sugar syrup content.', '{"matched_rules": ["HALAL_UNCERTAIN", "HIGH_SUGAR_WARNING"], "warnings": ["Missing Halal Cert", "High Sugar"]}', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 8: James Wong (user_id: 11) -> Egg (STRICT_AVOID), Vegetarian (STRICT_AVOID), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(36, 11, 8, '8886001234571', 'SAFE', '100% Plant-based vegetarian product with no egg or animal derivatives.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 12 DAY),
(37, 11, 8, '8886001234603', 'UNSAFE', 'Contains egg white powder, violating egg allergy and vegetarian constraints.', '{"matched_rules": ["EGG_ALLERGY"], "allergens_found": ["Egg White Powder"]}', NOW() - INTERVAL 9 DAY),
(38, 11, 8, '8886001234604', 'UNSAFE', 'Contains chicken extract broth (Non-Vegetarian).', '{"matched_rules": ["NON_VEGETARIAN"], "allergens_found": ["Chicken Extract"]}', NOW() - INTERVAL 7 DAY),
(39, 11, 8, '8886001234605', 'WARNING', 'Vegetarian and egg-free, but sodium level is high (750mg/100g).', '{"matched_rules": ["HIGH_SODIUM_WARNING"], "warnings": ["High Sodium"]}', NOW() - INTERVAL 4 DAY),
(40, 11, 8, '8886001234606', 'SAFE', 'Low sodium (150mg/100g) organic tofu, 100% vegetarian and egg-free.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 9: Olivia Wong (user_id: 11) -> Vegan (STRICT_AVOID), Low Trans Fat (PREFERENCE)
-- --------------------------------------------------------------------
(41, 11, 9, '8886001234607', 'SAFE', '100% Vegan certified oat milk, trans-fat free.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 13 DAY),
(42, 11, 9, '8886001234608', 'UNSAFE', 'Contains honey and milk solids (non-Vegan).', '{"matched_rules": ["NON_VEGAN_INGREDIENT"], "allergens_found": ["Honey", "Milk Solids"]}', NOW() - INTERVAL 10 DAY),
(43, 11, 9, '8886001234609', 'UNSAFE', 'Contains whey protein isolates and egg lecithin.', '{"matched_rules": ["NON_VEGAN_INGREDIENT"], "allergens_found": ["Whey Protein", "Egg Lecithin"]}', NOW() - INTERVAL 8 DAY),
(44, 11, 9, '8886001234610', 'WARNING', 'Vegan certified, but contains hydrogenated vegetable oil (0.8g trans fat/100g).', '{"matched_rules": ["HIGH_TRANS_FAT_WARNING"], "warnings": ["Trans Fat Exceeded"]}', NOW() - INTERVAL 3 DAY),
(45, 11, 9, '8886001234611', 'SAFE', 'Vegan soy yogurt, organic and 0g trans fat.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 10: Robert Wong (user_id: 11) -> Dairy (INTOLERANCE), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(46, 11, 10, '8886001234612', 'UNSAFE', 'Contains condensed milk and lactose (Dairy Intolerance).', '{"matched_rules": ["DAIRY_INTOLERANCE"], "allergens_found": ["Condensed Milk", "Lactose"]}', NOW() - INTERVAL 15 DAY),
(47, 11, 10, '8886001234613', 'SAFE', 'Lactose-free almond milk pudding with low sugar content (3g/100g).', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 11 DAY),
(48, 11, 10, '8886001234614', 'WARNING', 'Dairy-free soy drink, but contains high sucrose sugar levels (15g/100ml).', '{"matched_rules": ["HIGH_SUGAR_WARNING"], "warnings": ["High Sugar"]}', NOW() - INTERVAL 6 DAY),
(49, 11, 10, '8886001234615', 'UNSAFE', 'Contains butter fat and milk solids.', '{"matched_rules": ["DAIRY_INTOLERANCE"], "allergens_found": ["Butter Fat", "Milk Solids"]}', NOW() - INTERVAL 3 DAY),
(50, 11, 10, '8886001234616', 'SAFE', 'Dairy-free fruit sorbet with reduced sugar sweetener.', '{"matched_rules": [], "allergens_found": []}', NOW() - INTERVAL 1 DAY);
>>>>>>> Stashed changes

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
   NOW()),
   -- 1. Direct Allergen / Rule Match Log
(1, 1, 'TIER_1_RULES', NULL, NULL, NULL, 42, 
   '{"rule_engine": "deterministic_v1", "profile_id": 1}', 
   '{"status": "PASSED"}', 
   NOW() - INTERVAL 10 DAY),

-- 2. Direct Flag Log
(2, 2, 'TIER_1_RULES', NULL, NULL, NULL, 35, 
   '{"rule_engine": "deterministic_v1", "profile_id": 1}', 
   '{"status": "FLAGGED", "trigger": "GLUTEN_ALLERGY"}', 
   NOW() - INTERVAL 8 DAY),

-- 3. LLM Escalation Log for Complex Ingredient Analysis (e.g., E-numbers or compliance)
(3, 3, 'TIER_3_LLM', 'gpt-4o', 410, 110, 1180, 
   '{"system": "You are a food safety & nutrition specialist.", "user": "Evaluate sugar threshold and additives compliance."}', 
   '{"verdict": "WARNING", "reason": "Sugar level exceeds 10g/100g limit."}', 
   NOW() - INTERVAL 6 DAY),

-- 4. Direct Rule Match Log
(4, 4, 'TIER_1_RULES', NULL, NULL, NULL, 30, 
   '{"rule_engine": "deterministic_v1", "profile_id": 1}', 
   '{"status": "PASSED"}', 
   NOW() - INTERVAL 4 DAY),

-- 5. LLM Escalation Log (Halal / Complex Additive Reasoner)
(5, 16, 'TIER_3_LLM', 'gpt-4o', 450, 125, 1320, 
   '{"system": "You are a Halal food compliance auditor.", "user": "Analyze ingredient list for non-Halal E-numbers or additives."}', 
   '{"verdict": "WARNING", "reason": "Uncertain Halal status on ambiguous emulsifier."}', 
   NOW() - INTERVAL 15 DAY);