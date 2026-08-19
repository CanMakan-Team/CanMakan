-- =============================================
-- SCANS (Product Evaluation Events)
-- =============================================
INSERT INTO scans (id, user_id, profile_id, barcode, verdict, ai_explanation, findings_json, scanned_at) VALUES

-- --------------------------------------------------------------------
-- Profile 1: Sarah Tan (user_id: 4) -> Gluten (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(1, 4, 1, '95500539', 'SAFE', 'This product contains no gluten ingredients or wheat derivatives.', '[]', NOW() - INTERVAL 10 DAY),
(2, 4, 1, '0038527591039', 'UNSAFE', 'Contains wheat flour which violates the gluten-free constraint.', '[{"restrictionCode":"GLUTEN_ALLERGY","ingredientName":"Wheat Flour","reason":"Contains wheat flour which violates the gluten-free constraint."}]', NOW() - INTERVAL 8 DAY),
(3, 4, 1, '9300698500181', 'WARNING', 'High sugar content detected (18g/100g), exceeding preferred low sugar threshold.', '[{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"High sugar content detected (18g/100g), exceeding preferred low sugar threshold."}]', NOW() - INTERVAL 6 DAY),
(4, 4, 1, '4710154012793', 'SAFE', 'Gluten-free certified oats with low sugar content (2g/100g).', '[]', NOW() - INTERVAL 4 DAY),
(5, 4, 1, '675747001018', 'UNSAFE', 'Contains barley malt extract which contains gluten.', '[{"restrictionCode":"GLUTEN_ALLERGY","ingredientName":"Barley Malt Extract","reason":"Contains barley malt extract which contains gluten."}]', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 2: Michael Tan (user_id: 4) -> Low Fat (PREFERENCE), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(6, 4, 2, '9557305001368', 'SAFE', 'Product aligns with low fat (1.2g/100g) and low sodium (140mg/100g) preferences.', '[]', NOW() - INTERVAL 12 DAY),
(7, 4, 2, '8850581172007', 'WARNING', 'High sodium content detected (850mg/100g).', '[{"restrictionCode":"HIGH_SODIUM_WARNING","ingredientName":"Sodium","reason":"High sodium content detected (850mg/100g)."}]', NOW() - INTERVAL 9 DAY),
(8, 4, 2, '9313839005087', 'WARNING', 'High fat content detected (22g total fat/100g).', '[{"restrictionCode":"HIGH_FAT_WARNING","ingredientName":"Total Fat","reason":"High fat content detected (22g total fat/100g)."}]', NOW() - INTERVAL 7 DAY),
(9, 4, 2, '9311983909800', 'SAFE', 'Low sodium (120mg/100g) and low fat (1.5g/100g) steamed rice cake snack.', '[]', NOW() - INTERVAL 3 DAY),
(10, 4, 2, '8888077103549', 'WARNING', 'Contains both elevated total fat (15g/100g) and high sodium (600mg/100g).', '[{"restrictionCode":"HIGH_FAT_WARNING","ingredientName":"Fat","reason":"Contains both elevated total fat (15g/100g) and high sodium (600mg/100g)."},{"restrictionCode":"HIGH_SODIUM_WARNING","ingredientName":"Sodium","reason":"Contains both elevated total fat (15g/100g) and high sodium (600mg/100g)."}]', NOW()),

-- --------------------------------------------------------------------
-- Profile 3: Emily Tan (user_id: 4) -> Dairy (INTOLERANCE), Peanut (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(11, 4, 3, '07321122', 'UNSAFE', 'Contains peanuts which violates the user severe peanut allergy constraint.', '[{"restrictionCode":"PEANUT_ALLERGY","ingredientName":"Peanuts","reason":"Contains peanuts which violates the user severe peanut allergy constraint."}]', NOW() - INTERVAL 14 DAY),
(12, 4, 3, '8888077102092', 'UNSAFE', 'Contains milk solids and whey powder (Lactose/Dairy Intolerance).', '[{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Milk Solids","reason":"Contains milk solids and whey powder (Lactose/Dairy Intolerance)."},{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Whey","reason":"Contains milk solids and whey powder (Lactose/Dairy Intolerance)."}]', NOW() - INTERVAL 11 DAY),
(13, 4, 3, '9319530000239', 'SAFE', 'Dairy-free, peanut-free almond-based rice crackers with zero added sugar.', '[]', NOW() - INTERVAL 8 DAY),
(14, 4, 3, '4710154012793', 'WARNING', 'No peanut or dairy allergens, but sugar levels are high (24g/100g).', '[{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"No peanut or dairy allergens, but sugar levels are high (24g/100g)."}]', NOW() - INTERVAL 5 DAY),
(15, 4, 3, '9315536220107', 'UNSAFE', 'Manufactured in a facility that processes peanuts and tree nuts.', '[{"restrictionCode":"CROSS_CONTAMINATION_PEANUT","ingredientName":"Trace Peanuts","reason":"Manufactured in a facility that processes peanuts and tree nuts."}]', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 4: David Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Trans Fat (PREFERENCE)
-- --------------------------------------------------------------------
(16, 7, 4, '9317276000032', 'WARNING', 'Product is not Halal certified but does not explicitly list non-halal ingredients.', '[{"restrictionCode":"HALAL_UNCERTAIN","ingredientName":"Missing Halal Certification","reason":"Product is not Halal certified but does not explicitly list non-halal ingredients."}]', NOW() - INTERVAL 15 DAY),
(17, 7, 4, '6916063230510', 'UNSAFE', 'Contains pork gelatin and mirin (alcohol), strictly non-Halal.', '[{"restrictionCode":"NON_HALAL_INGREDIENT","ingredientName":"Pork Gelatin","reason":"Contains pork gelatin and mirin (alcohol), strictly non-Halal."},{"restrictionCode":"NON_HALAL_INGREDIENT","ingredientName":"Mirin","reason":"Contains pork gelatin and mirin (alcohol), strictly non-Halal."}]', NOW() - INTERVAL 12 DAY),
(18, 7, 4, '8997035600041', 'SAFE', 'Certified Halal by MUIS Singapore and contains 0g trans fat.', '[]', NOW() - INTERVAL 9 DAY),
(19, 7, 4, '8888383208648', 'WARNING', 'Certified Halal, but contains partially hydrogenated oil (high trans fat: 0.8g/100g).', '[{"restrictionCode":"HIGH_TRANS_FAT_WARNING","ingredientName":"Trans Fat","reason":"Certified Halal, but contains partially hydrogenated oil (high trans fat: 0.8g/100g)."}]', NOW() - INTERVAL 4 DAY),
(20, 7, 4, '8888440000048', 'SAFE', 'Certified Halal organic coconut water, completely trans-fat free.', '[]', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 5: Jessica Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Fat (PREFERENCE), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(21, 7, 5, '4901330300067', 'UNSAFE', 'Contains lard (pork fat), non-Halal.', '[{"restrictionCode":"NON_HALAL_INGREDIENT","ingredientName":"Lard","reason":"Contains lard (pork fat), non-Halal."}]', NOW() - INTERVAL 13 DAY),
(22, 7, 5, '9557305001368', 'SAFE', 'MUIS Halal certified, low fat (1g/100g), and low sodium (180mg/100g).', '[]', NOW() - INTERVAL 10 DAY),
(23, 7, 5, '9316434288572', 'WARNING', 'Halal certified, but contains high total fat (18g/100g).', '[{"restrictionCode":"HIGH_FAT_WARNING","ingredientName":"Fat","reason":"Halal certified, but contains high total fat (18g/100g)."}]', NOW() - INTERVAL 7 DAY),
(24, 7, 5, '8850581172007', 'WARNING', 'Halal certified, but sodium level is high (920mg/100g).', '[{"restrictionCode":"HIGH_SODIUM_WARNING","ingredientName":"Sodium","reason":"Halal certified, but sodium level is high (920mg/100g)."}]', NOW() - INTERVAL 3 DAY),
(25, 7, 5, '8888196305817', 'SAFE', 'Halal certified steamed chicken broth, low fat and reduced sodium.', '[]', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 6: Daniel Lim (user_id: 7) -> Shellfish (STRICT_AVOID), Halal (STRICT_AVOID)
-- --------------------------------------------------------------------
(26, 7, 6, '8885014850160', 'UNSAFE', 'Contains shrimp extract, which poses a severe risk due to shellfish allergy.', '[{"restrictionCode":"SHELLFISH_ALLERGY","ingredientName":"Shrimp Extract","reason":"Contains shrimp extract, which poses a severe risk due to shellfish allergy."}]', NOW() - INTERVAL 11 DAY),
(27, 7, 6, '8888279640019', 'UNSAFE', 'Contains crab meat paste and non-Halal flavoring agent.', '[{"restrictionCode":"SHELLFISH_ALLERGY","ingredientName":"Crab Paste","reason":"Contains crab meat paste and non-Halal flavoring agent."},{"restrictionCode":"NON_HALAL_INGREDIENT","ingredientName":"Non-Halal flavoring agent","reason":"Contains crab meat paste and non-Halal flavoring agent."}]', NOW() - INTERVAL 9 DAY),
(28, 7, 6, '8888077103549', 'SAFE', 'Certified Halal potato chips with zero crustacean or shellfish processing.', '[]', NOW() - INTERVAL 6 DAY),
(29, 7, 6, '8885014850160', 'UNSAFE', 'Contains oyster sauce (shellfish derivative).', '[{"restrictionCode":"SHELLFISH_ALLERGY","ingredientName":"Oyster Sauce","reason":"Contains oyster sauce (shellfish derivative)."}]', NOW() - INTERVAL 4 DAY),
(30, 7, 6, '8888440000048', 'SAFE', 'Halal vegetable spring rolls free from shellfish and seafood.', '[]', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 7: Amanda Lim (user_id: 7) -> Halal (STRICT_AVOID), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(31, 7, 7, '9557305001368', 'SAFE', 'Certified Halal low-sugar fruit juice (3g sugar/100ml).', '[]', NOW() - INTERVAL 14 DAY),
(32, 7, 7, '8801123600328', 'UNSAFE', 'Contains uncertified beef gelatin (non-Halal).', '[{"restrictionCode":"NON_HALAL_INGREDIENT","ingredientName":"Uncertified Gelatin","reason":"Contains uncertified beef gelatin (non-Halal)."}]', NOW() - INTERVAL 10 DAY),
(33, 7, 7, '8888196305817', 'WARNING', 'Halal certified milk tea, but contains high added sugar (16g/100ml).', '[{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"Halal certified milk tea, but contains high added sugar (16g/100ml)."}]', NOW() - INTERVAL 7 DAY),
(34, 7, 7, '8888440000048', 'SAFE', 'MUIS Halal yogurt with zero added cane sugar.', '[]', NOW() - INTERVAL 5 DAY),
(35, 7, 7, '9556771000028', 'WARNING', 'Uncertain Halal status and high sugar syrup content.', '[{"restrictionCode":"HALAL_UNCERTAIN","ingredientName":"Missing Halal Cert","reason":"Uncertain Halal status and high sugar syrup content."},{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"Uncertain Halal status and high sugar syrup content."}]', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 8: James Wong (user_id: 11) -> Egg (STRICT_AVOID), Vegetarian (STRICT_AVOID), Low Sodium (PREFERENCE)
-- --------------------------------------------------------------------
(36, 11, 8, '9311983909800', 'SAFE', '100% Plant-based vegetarian product with no egg or animal derivatives.', '[]', NOW() - INTERVAL 12 DAY),
(37, 11, 8, '8854651008845', 'UNSAFE', 'Contains egg white powder, violating egg allergy and vegetarian constraints.', '[{"restrictionCode":"EGG_ALLERGY","ingredientName":"Egg White Powder","reason":"Contains egg white powder, violating egg allergy and vegetarian constraints."}]', NOW() - INTERVAL 9 DAY),
(38, 11, 8, '8888383208648', 'UNSAFE', 'Contains chicken extract broth (Non-Vegetarian).', '[{"restrictionCode":"NON_VEGETARIAN","ingredientName":"Chicken Extract","reason":"Contains chicken extract broth (Non-Vegetarian)."}]', NOW() - INTERVAL 7 DAY),
(39, 11, 8, '8888077103549', 'WARNING', 'Vegetarian and egg-free, but sodium level is high (750mg/100g).', '[{"restrictionCode":"HIGH_SODIUM_WARNING","ingredientName":"Sodium","reason":"Vegetarian and egg-free, but sodium level is high (750mg/100g)."}]', NOW() - INTERVAL 4 DAY),
(40, 11, 8, '4978045600683', 'SAFE', 'Low sodium (150mg/100g) organic tofu, 100% vegetarian and egg-free.', '[]', NOW() - INTERVAL 2 DAY),

-- --------------------------------------------------------------------
-- Profile 9: Olivia Wong (user_id: 11) -> Vegan (STRICT_AVOID), Low Trans Fat (PREFERENCE)
-- --------------------------------------------------------------------
(41, 11, 9, '4710154012793', 'SAFE', '100% Vegan certified oat milk, trans-fat free.', '[]', NOW() - INTERVAL 13 DAY),
(42, 11, 9, '5000119120656', 'UNSAFE', 'Contains honey and milk solids (non-Vegan).', '[{"restrictionCode":"NON_VEGAN_INGREDIENT","ingredientName":"Honey","reason":"Contains honey and milk solids (non-Vegan)."},{"restrictionCode":"NON_VEGAN_INGREDIENT","ingredientName":"Milk Solids","reason":"Contains honey and milk solids (non-Vegan)."}]', NOW() - INTERVAL 10 DAY),
(43, 11, 9, '8888077102092', 'UNSAFE', 'Contains whey protein isolates and egg lecithin.', '[{"restrictionCode":"NON_VEGAN_INGREDIENT","ingredientName":"Whey Protein","reason":"Contains whey protein isolates and egg lecithin."},{"restrictionCode":"NON_VEGAN_INGREDIENT","ingredientName":"Egg Lecithin","reason":"Contains whey protein isolates and egg lecithin."}]', NOW() - INTERVAL 8 DAY),
(44, 11, 9, '9316434288671', 'WARNING', 'Vegan certified, but contains hydrogenated vegetable oil (0.8g trans fat/100g).', '[{"restrictionCode":"HIGH_TRANS_FAT_WARNING","ingredientName":"Trans Fat","reason":"Vegan certified, but contains hydrogenated vegetable oil (0.8g trans fat/100g)."}]', NOW() - INTERVAL 3 DAY),
(45, 11, 9, '4978045600683', 'SAFE', 'Vegan soy yogurt, organic and 0g trans fat.', '[]', NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Profile 10: Robert Wong (user_id: 11) -> Dairy (INTOLERANCE), Low Sugar (PREFERENCE)
-- --------------------------------------------------------------------
(46, 11, 10, '9557305000545', 'UNSAFE', 'Contains condensed milk and lactose (Dairy Intolerance).', '[{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Condensed Milk","reason":"Contains condensed milk and lactose (Dairy Intolerance)."},{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Lactose","reason":"Contains condensed milk and lactose (Dairy Intolerance)."}]', NOW() - INTERVAL 15 DAY),
(47, 11, 10, '4987176009913', 'SAFE', 'Lactose-free almond milk pudding with low sugar content (3g/100g).', '[]', NOW() - INTERVAL 11 DAY),
(48, 11, 10, '4710154012793', 'WARNING', 'Dairy-free soy drink, but contains high sucrose sugar levels (15g/100ml).', '[{"restrictionCode":"HIGH_SUGAR_WARNING","ingredientName":"Sugar","reason":"Dairy-free soy drink, but contains high sucrose sugar levels (15g/100ml)."}]', NOW() - INTERVAL 6 DAY),
(49, 11, 10, '8888440000048', 'UNSAFE', 'Contains butter fat and milk solids.', '[{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Butter Fat","reason":"Contains butter fat and milk solids."},{"restrictionCode":"DAIRY_INTOLERANCE","ingredientName":"Milk Solids","reason":"Contains butter fat and milk solids."}]', NOW() - INTERVAL 3 DAY),
(50, 11, 10, '9311983909800', 'SAFE', 'Dairy-free fruit sorbet with reduced sugar sweetener.', '[]', NOW() - INTERVAL 1 DAY);

-- =============================================
--  AI EXECUTION LOGS (Audit & Diagnostic Trail)
-- =============================================
INSERT INTO ai_execution_logs (id, scan_id, execution_tier, model_id, prompt_tokens, completion_tokens, latency_ms, compiled_prompt, raw_llm_response, created_at) VALUES
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
