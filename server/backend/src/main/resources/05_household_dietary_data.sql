-- =============================================
--  FAMILIES
-- =============================================
INSERT INTO families (id, family_name, created_by_user_id, created_at) VALUES
(1, 'Tan Family', 4, NOW()),
(2, 'Lim Family', 7, NOW()),
(3, 'Wong Family', 11, NOW());

-- =============================================
--  FAMILY MEMBERS (Assigning 10 App Users)
-- =============================================
INSERT INTO family_members (family_id, user_id, member_role, is_active, joined_at) VALUES
-- Tan Family Members (Users 4, 5, 6)
(1, 4, 'PRIMARY_ADMIN', TRUE, NOW()),
(1, 5, 'MEMBER', TRUE, NOW()),
(1, 6, 'MEMBER', TRUE, NOW()),

-- Lim Family Members (Users 7, 8, 9, 10)
(2, 7, 'PRIMARY_ADMIN', TRUE, NOW()),
(2, 8, 'MEMBER', TRUE, NOW()),
(2, 9, 'MEMBER', FALSE, NOW()),
(2, 10, 'MEMBER', TRUE, NOW()),

-- Wong Family Members (Users 11, 12, 13)
(3, 11, 'PRIMARY_ADMIN', TRUE, NOW()),
(3, 12, 'MEMBER', TRUE, NOW()),
(3, 13, 'MEMBER', TRUE, NOW());

-- =============================================
--  DIETARY PROFILES
-- =============================================
INSERT INTO dietary_profiles (id, family_id, linked_user_id, profile_name, relationship, is_primary, avatar_url, created_at, updated_at) VALUES
-- Tan Family Profiles
(1, 1, 4, 'Sarah Tan', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/sarah.svg', NOW(), NOW()),
(2, 1, 5, 'Michael Tan', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/michael.svg', NOW(), NOW()),
(3, 1, 6, 'Emily Tan', 'DEPENDANT', 0, 'https://api.dicebear.com/7.x/avatars/emily.svg', NOW(), NOW()),

-- Lim Family Profiles
(4, 2, 7, 'David Lim', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/david.svg', NOW(), NOW()),
(5, 2, 8, 'Jessica Lim', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/jessica.svg', NOW(), NOW()),
(6, 2, 9, 'Daniel Lim', 'DEPENDANT', 0, 'https://api.dicebear.com/7.x/avatars/daniel.svg', NOW(), NOW()),
(7, 2, 10, 'Amanda Lim', 'DEPENDANT', 0, 'https://api.dicebear.com/7.x/avatars/amanda.svg', NOW(), NOW()),

-- Wong Family Profiles
(8, 3, 11, 'James Wong', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/james.svg', NOW(), NOW()),
(9, 3, 12, 'Olivia Wong', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/olivia.svg', NOW(), NOW()),
(10, 3, 13, 'Robert Wong', 'DEPENDANT', 0, 'https://api.dicebear.com/7.x/avatars/robert.svg', NOW(), NOW());

-- =============================================
--  DIETARY RESTRICTIONS (Reference Data)
-- =============================================
INSERT INTO dietary_restrictions (id, code, display_name, category, description) VALUES
(1, 'GLUTEN', 'Gluten Free', 'ALLERGEN', 'Strictly avoid wheat, barley, rye, and oat gluten.'),
-- Code stays 'DAIRY' (not 'DAIRY_FREE'): it is the same value stamped on every
-- dairy-tagged ingredient's root_allergen column, and AllergenChecker matches
-- restrictions to ingredients by exact code equality. Renaming it here without
-- also touching ~30 ingredient rows would silently stop all dairy matching.
(2, 'DAIRY', 'Dairy Free', 'ALLERGEN', 'Avoid milk solids, lactose, whey, and dairy fats.'),
(3, 'PEANUT', 'Peanut Allergy', 'ALLERGEN', 'Severe reaction to peanuts and peanut derivatives.'),
(4, 'SHELLFISH', 'Shellfish Allergy', 'ALLERGEN', 'Avoid crab, shrimp, lobster, and shellfish extracts.'),
(5, 'FISH', 'Fish Allergy', 'ALLERGEN', 'Avoid bony fish, anchovies, bonito, and fish surimi.'),
(6, 'SOY', 'Soy Allergy', 'ALLERGEN', 'Avoid soy lecithin, miso, and soybean derivatives.'),
(7, 'EGG', 'Egg Allergy', 'ALLERGEN', 'Avoid eggs and egg powder.'),
(8, 'HALAL', 'Halal', 'RELIGIOUS', 'Requires Halal-certified ingredients and no pork or alcohol.'),
(9, 'VEGETARIAN', 'Vegetarian', 'DIET', 'Does not consume meat, poultry, or seafood.'),
(10, 'VEGAN', 'Vegan', 'DIET', 'Avoids animal-derived ingredients'),
(11,'LOW_SUGAR', 'Low Sugar', 'DIET', 'Checks sugar per 100 g'),
(12, 'LOW_FAT', 'Low Fat', 'DIET', 'Checks total fat per 100 g'),
(13, 'LOW_TRANS_FAT', 'Low Trans Fat', 'DIET', 'Checks trans fat per 100 g'),
(14, 'LOW_SODIUM', 'Low Salt', 'DIET', 'Checks sodium per 100 g'),
-- Code renamed from 'HINDU' to 'KOSHER' to match the display name: confirmed
-- nothing depends on this id (no ingredient_restrictions rows reference id 15, and
-- ReligiousChecker only ever matches the HALAL code, ignoring everything
-- else), so this is a safe rename rather than a DAIRY-style alias situation.
(15, 'KOSHER', 'Kosher', 'RELIGIOUS', 'Requires kosher-certified ingredients; forbids pork and shellfish, and does not mix meat with dairy'),
-- New: mirrors the web portal, which offers Dairy Free and Lactose Intolerant
-- as two separate options. AllergenChecker and DietaryRuleEngine treat this
-- code as an alias of DAIRY so it flags the same dairy ingredients (see the
-- comment on id 2) rather than silently matching nothing.
(16, 'LACTOSE_INTOLERANT', 'Lactose Intolerant', 'ALLERGEN', 'Avoid lactose found in milk and dairy products.'),
-- Code matches the 'TREE_NUT' root_allergen already used by ingredient rows
-- (e.g. Hazelnut, Cashew Nuts), so this one is immediately functional.
(17, 'TREE_NUT', 'Tree Nut Allergy', 'ALLERGEN', 'Avoid almonds, cashews, hazelnuts, walnuts, and other tree nuts.'),
-- No ingredient in the catalog is tagged 'SESAME' yet, so this behaves like
-- Kosher: selectable, but currently produces no automated scan findings.
(18, 'SESAME', 'Sesame Allergy', 'ALLERGEN', 'Avoid sesame seeds, tahini, and sesame oil.'),
-- No cholesterol field exists on the nutrition data yet, so NutritionChecker
-- does not evaluate this restriction (same inert-until-implemented status as
-- Kosher/Sesame above).
(19, 'LOW_CHOLESTEROL', 'Low Cholesterol', 'DIET', 'Checks cholesterol per 100 g'),
-- No macro-ratio checking exists yet, so this is selectable but inert for now.
(20, 'KETO', 'Keto', 'DIET', 'Very low carbohydrate, high fat diet') ;

-- =============================================
-- PROFILE RESTRICTIONS (Junction Table)
-- =============================================
INSERT INTO profile_restrictions (dietary_profile_id, dietary_restriction_id, severity_level) VALUES
-- 1. Sarah Tan (Gluten Allergy + Low Sugar)
(1, 1, 'STRICT_AVOID'),
(1, 11, 'PREFERENCE'),

-- 2. Michael Tan (Low Fat & Low Sodium)
(2, 12, 'PREFERENCE'),
(2, 14, 'PREFERENCE'),

-- 3. Emily Tan (Peanut Allergy & Dairy Intolerance + Low Sugar)
(3, 2, 'INTOLERANCE'),
(3, 3, 'STRICT_AVOID'),
(3, 11, 'PREFERENCE'),

-- 4. David Lim (Halal Requirement + Low Trans Fat)
(4, 8, 'STRICT_AVOID'),
(4, 13, 'PREFERENCE'),

-- 5. Jessica Lim (Halal Requirement + Low Fat & Low Sodium)
(5, 8, 'STRICT_AVOID'),
(5, 12, 'PREFERENCE'),
(5, 14, 'PREFERENCE'),

-- 6. Daniel Lim (Halal + Shellfish Allergy)
(6, 4, 'STRICT_AVOID'),
(6, 8, 'STRICT_AVOID'),

-- 7. Amanda Lim (Halal + Low Sugar)
(7, 8, 'STRICT_AVOID'),
(7, 11, 'PREFERENCE'),

-- 8. James Wong (Vegetarian & Egg Allergy + Low Sodium)
(8, 7, 'STRICT_AVOID'),
(8, 9, 'STRICT_AVOID'),
(8, 14, 'PREFERENCE'),

-- 9. Olivia Wong (Vegan Diet & Low Trans Fat)
(9, 10, 'STRICT_AVOID'),
(9, 13, 'PREFERENCE'),

-- 10. Robert Wong (Dairy Intolerance + Low Sugar)
(10, 2, 'INTOLERANCE'),
(10, 11, 'PREFERENCE');


