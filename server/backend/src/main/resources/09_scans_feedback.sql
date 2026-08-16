-- =============================================
-- SCANS FEEDBACK (UC20 report incorrect product info)
-- =============================================
-- Thumbs up/down reactions to a scan verdict, each tied to a specific row in
-- `scans`. In practice most users only bother leaving feedback when something
-- seems wrong, so negative (thumbs down) rows outnumber positive (thumbs up)
-- ones here, and most of the negative rows carry an elaboration comment.
--
-- The negative comments are grounded in complaints that are common for real
-- crowd-sourced food-scanning/allergen apps: stale ingredient data after a
-- product reformulation, a barcode entry mixed up with a different SKU of the
-- same product line, regional recipe/certification differences, and missed
-- "may contain traces of" cross-contamination warnings. Positive comments are
-- intentionally left null — the app's thumbs up has no comment box, so a
-- thumbs up never carries elaboration text.
--
-- Rows 1-20 cover Sarah, Michael, Emily, David and James (see below). Rows
-- 21-34 are a second batch of scans for Michael, Emily, Jessica, Daniel,
-- Amanda, Olivia and Robert. Across all 34 rows: 23 negative / 11 positive,
-- and 17 of the 23 negative rows (~74%) carry a comment.

INSERT INTO scans_feedback (id, scan_id, is_positive, user_comments, resolved, created_at) VALUES

-- --------------------------------------------------------------------
-- Negative feedback (thumbs down) — 14 rows, 10 of them (~71%) with a comment
-- --------------------------------------------------------------------

-- Scan 1: Sarah Tan (Gluten STRICT_AVOID) — marked SAFE, "no gluten ingredients or wheat derivatives"
(1, 1, FALSE,
 'I reacted after eating this — the ingredient list here hasn''t been updated since the product was reformulated to include barley malt extract. Please double check, this should not be marked safe.',
 FALSE, NOW() - INTERVAL 9 DAY),

-- Scan 3: Sarah Tan — WARNING for high sugar (18g/100g)
(2, 3, FALSE,
 'This is the diet/lite version I bought — sugar is only 4g/100g on my actual pack. The database entry looks like it''s for the regular version of this product.',
 FALSE, NOW() - INTERVAL 5 DAY),

-- Scan 7: Michael Tan (Low Sodium PREFERENCE) — WARNING for high sodium (850mg/100g)
(3, 7, FALSE, NULL, FALSE, NOW() - INTERVAL 8 DAY),

-- Scan 16: David Lim (Halal STRICT_AVOID) — WARNING, "not Halal certified but no explicit non-halal ingredients"
(4, 16, FALSE,
 'Checked the brand''s official website — this product actually carries MUIS Halal certification now. The verdict here looks outdated.',
 TRUE, NOW() - INTERVAL 13 DAY),

-- Scan 19: David Lim — WARNING, Butter Chicken Biryani flagged for partially hydrogenated oil (0.8g trans fat)
(5, 19, FALSE,
 'The pack I have says "trans fat free" and lists sunflower oil, not partially hydrogenated oil. Might be an old product photo in the database.',
 TRUE, NOW() - INTERVAL 3 DAY),

-- Scan 23: Jessica Lim (Low Fat PREFERENCE) — WARNING for high total fat (18g/100g)
(6, 23, FALSE, NULL, FALSE, NOW() - INTERVAL 6 DAY),

-- Scan 33: Amanda Lim (Halal STRICT_AVOID, Low Sugar PREFERENCE) — WARNING, halal milk tea with high added sugar
(7, 33, FALSE,
 'I always buy the less-sweet version from this brand — this looks like it''s using nutrition data for a different SKU under the same barcode prefix.',
 FALSE, NOW() - INTERVAL 5 DAY),

-- Scan 35: Amanda Lim — WARNING, "uncertain Halal status and high sugar syrup content"
(8, 35, FALSE,
 'This brand is JAKIM-certified halal in Malaysia — the app should recognise regional certification bodies too, not just MUIS.',
 FALSE, NOW() - INTERVAL 12 HOUR),

-- Scan 39: James Wong (Low Sodium PREFERENCE) — WARNING, vegetarian/egg-free but high sodium (750mg/100g)
(9, 39, FALSE, NULL, FALSE, NOW() - INTERVAL 2 DAY),

-- Scan 44: Olivia Wong (Vegan STRICT_AVOID, Low Trans Fat PREFERENCE) — WARNING for hydrogenated oil trans fat
(10, 44, FALSE,
 'This brand reformulated a few months ago to remove hydrogenated oil — I can confirm from the ingredients list on my own pack, no partially hydrogenated oil listed anymore.',
 FALSE, NOW() - INTERVAL 1 DAY),

-- Scan 48: Robert Wong (Dairy INTOLERANCE, Low Sugar PREFERENCE) — WARNING, dairy-free soy drink with high sucrose
(11, 48, FALSE,
 'There''s also an unsweetened version of this soy drink that seems to share the same barcode prefix — might be getting mixed up in the database.',
 FALSE, NOW() - INTERVAL 4 DAY),

-- Scan 9: Michael Tan — marked SAFE, "low sodium and low fat" steamed rice cake snack
(12, 9, FALSE,
 'My blood pressure spiked after this — the sodium on my actual pack (an imported batch) says 310mg/100g, not 120mg. Looks like a regional formulation difference that wasn''t caught.',
 FALSE, NOW() - INTERVAL 2 DAY),

-- Scan 13: Emily Tan (Peanut STRICT_AVOID) — marked SAFE, "dairy-free, peanut-free almond-based rice crackers"
(13, 13, FALSE,
 'The pack says "may contain traces of tree nuts and peanuts" in small print on the back — this cross-contamination warning wasn''t picked up by the scan.',
 FALSE, NOW() - INTERVAL 6 DAY),

-- Scan 30: Daniel Lim (Shellfish STRICT_AVOID) — marked SAFE, halal vegetable spring rolls "free from shellfish and seafood"
(14, 30, FALSE, NULL, FALSE, NOW() - INTERVAL 1 DAY),

-- --------------------------------------------------------------------
-- Positive feedback (thumbs up) — 6 rows; no comment box on thumbs up, so
-- user_comments is always null here, matching what the app actually sends.
-- --------------------------------------------------------------------

-- Scan 2: Sarah Tan — correctly caught wheat flour as UNSAFE for a gluten allergy
(15, 2, TRUE, NULL, FALSE, NOW() - INTERVAL 7 DAY),

-- Scan 18: David Lim — correctly confirmed SAFE, MUIS Halal certified with 0g trans fat
(16, 18, TRUE, NULL, FALSE, NOW() - INTERVAL 8 DAY),

-- Scan 37: James Wong — correctly caught egg white powder as UNSAFE for an egg allergy
(17, 37, TRUE, NULL, FALSE, NOW() - INTERVAL 7 DAY),

-- Scan 42: Olivia Wong — correctly caught honey and milk solids as UNSAFE for a vegan diet
(18, 42, TRUE, NULL, FALSE, NOW() - INTERVAL 9 DAY),

-- Scan 47: Robert Wong — correctly confirmed SAFE, lactose-free almond milk pudding, low sugar
(19, 47, TRUE, NULL, FALSE, NOW() - INTERVAL 10 DAY),

-- Scan 26: Daniel Lim — correctly caught shrimp extract as UNSAFE for a shellfish allergy
(20, 26, TRUE, NULL, FALSE, NOW() - INTERVAL 9 DAY),

-- --------------------------------------------------------------------
-- Second batch — additional scans for Michael, Emily, Jessica, Daniel,
-- Amanda, Olivia and Robert (9 negative, 5 positive; 7 of 9 negative rows
-- carry a comment).
-- --------------------------------------------------------------------

-- Scan 6: Michael Tan (Low Fat/Low Sodium PREFERENCE) — marked SAFE, "low fat (1.2g/100g) and low sodium (140mg/100g)"
(21, 6, FALSE,
 'I bought this at a different store and the label shows 380mg sodium per 100g, not 140mg — might be tracking data for a smaller pack size or an older recipe.',
 FALSE, NOW() - INTERVAL 10 DAY),

-- Scan 8: Michael Tan — WARNING, high fat content (22g total fat/100g)
(22, 8, FALSE,
 'This is the baked version of the snack, not fried — my pack lists 6g fat per 100g. Feels like the database might have the wrong variant linked to this barcode.',
 FALSE, NOW() - INTERVAL 6 DAY),

-- Scan 15: Emily Tan (Peanut STRICT_AVOID) — correctly caught UNSAFE for peanut/tree nut cross-contamination
(23, 15, TRUE, NULL, FALSE, NOW() - INTERVAL 1 DAY),

-- Scan 14: Emily Tan — WARNING, no allergens but high sugar (24g/100g)
(24, 14, FALSE,
 'The brand cut the sugar content in their last reformulation — new pack says 9g/100g. Ingredient data here looks like it''s from before the change.',
 FALSE, NOW() - INTERVAL 3 DAY),

-- Scan 21: Jessica Lim (Halal STRICT_AVOID) — correctly caught lard as UNSAFE/non-Halal
(25, 21, TRUE, NULL, FALSE, NOW() - INTERVAL 11 DAY),

-- Scan 24: Jessica Lim — WARNING, halal certified but sodium level high (920mg/100g)
(26, 24, FALSE,
 'My pack (bought last month) shows 540mg sodium per 100g — looks like the brand lowered sodium since this entry was last updated.',
 FALSE, NOW() - INTERVAL 2 DAY),

-- Scan 27: Daniel Lim (Shellfish/Halal STRICT_AVOID) — correctly caught crab meat paste and non-Halal flavoring
(27, 27, TRUE, NULL, FALSE, NOW() - INTERVAL 7 DAY),

-- Scan 29: Daniel Lim — UNSAFE, oyster sauce (shellfish derivative)
(28, 29, FALSE,
 'This brand switched to a mushroom-based sauce substitute earlier this year — the ingredient list on my bottle doesn''t mention oyster sauce or any shellfish derivative anymore.',
 FALSE, NOW() - INTERVAL 2 DAY),

-- Scan 32: Amanda Lim (Halal STRICT_AVOID) — correctly caught uncertified beef gelatin as non-Halal
(29, 32, TRUE, NULL, FALSE, NOW() - INTERVAL 8 DAY),

-- Scan 31: Amanda Lim — marked SAFE, certified Halal low-sugar fruit juice
(30, 31, FALSE, NULL, FALSE, NOW() - INTERVAL 12 DAY),

-- Scan 43: Olivia Wong (Vegan STRICT_AVOID) — correctly caught whey protein and egg lecithin as non-vegan
(31, 43, TRUE, NULL, FALSE, NOW() - INTERVAL 6 DAY),

-- Scan 41: Olivia Wong — marked SAFE, 100% vegan certified oat milk
(32, 41, FALSE,
 'This barcode seems to be shared between the original and the barista blend — the barista version has added sugar and a different fat profile, so the safe verdict might not apply to what I actually scanned.',
 FALSE, NOW() - INTERVAL 11 DAY),

-- Scan 46: Robert Wong (Dairy INTOLERANCE) — UNSAFE, condensed milk and lactose
(33, 46, FALSE,
 'I specifically bought the new lactose-free version of this — packaging clearly says "Lactose Free" on the front. The scan might be matching the wrong product entry.',
 TRUE, NOW() - INTERVAL 13 DAY),

-- Scan 49: Robert Wong — UNSAFE, butter fat and milk solids
(34, 49, FALSE, NULL, FALSE, NOW() - INTERVAL 1 DAY);
