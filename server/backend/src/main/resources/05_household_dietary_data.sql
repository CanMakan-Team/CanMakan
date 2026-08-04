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
INSERT INTO family_members (family_id, user_id, member_role, joined_at) VALUES
-- Tan Family Members (Users 4, 5, 6)
(1, 4, 'PRIMARY_ADMIN', NOW()),
(1, 5, 'MEMBER', NOW()),
(1, 6, 'MEMBER', NOW()),

-- Lim Family Members (Users 7, 8, 9, 10)
(2, 7, 'PRIMARY_ADMIN', NOW()),
(2, 8, 'MEMBER', NOW()),
(2, 9, 'MEMBER', NOW()),
(2, 10, 'MEMBER', NOW()),

-- Wong Family Members (Users 11, 12, 13)
(3, 11, 'PRIMARY_ADMIN', NOW()),
(3, 12, 'MEMBER', NOW()),
(3, 13, 'MEMBER', NOW());

-- =============================================
--  DIETARY PROFILES
-- =============================================
INSERT INTO dietary_profiles (id, family_id, linked_user_id, profile_name, relationship, is_primary, avatar_url, created_at, updated_at) VALUES
-- Tan Family Profiles
(1, 1, 4, 'Sarah Tan', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/sarah.svg', NOW(), NOW()),
(2, 1, 5, 'Michael Tan', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/michael.svg', NOW(), NOW()),
(3, 1, 6, 'Emily Tan', 'DEPENDENT', 0, 'https://api.dicebear.com/7.x/avatars/emily.svg', NOW(), NOW()),

-- Lim Family Profiles
(4, 2, 7, 'David Lim', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/david.svg', NOW(), NOW()),
(5, 2, 8, 'Jessica Lim', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/jessica.svg', NOW(), NOW()),
(6, 2, 9, 'Daniel Lim', 'DEPENDENT', 0, 'https://api.dicebear.com/7.x/avatars/daniel.svg', NOW(), NOW()),
(7, 2, 10, 'Amanda Lim', 'DEPENDENT', 0, 'https://api.dicebear.com/7.x/avatars/amanda.svg', NOW(), NOW()),

-- Wong Family Profiles
(8, 3, 11, 'James Wong', 'SELF', 1, 'https://api.dicebear.com/7.x/avatars/james.svg', NOW(), NOW()),
(9, 3, 12, 'Olivia Wong', 'SPOUSE', 0, 'https://api.dicebear.com/7.x/avatars/olivia.svg', NOW(), NOW()),
(10, 3, 13, 'Robert Wong', 'DEPENDENT', 0, 'https://api.dicebear.com/7.x/avatars/robert.svg', NOW(), NOW());

-- =============================================
--  DIETARY RESTRICTIONS (Reference Data)
-- =============================================
INSERT INTO dietary_restrictions (id, code, display_name, category, description) VALUES
(1, 'GLUTEN', 'Gluten Free', 'ALLERGEN', 'Strictly avoid wheat, barley, rye, and oat gluten.'),
(2, 'DAIRY', 'Lactose / Dairy Intolerance', 'ALLERGEN', 'Avoid milk solids, lactose, whey, and dairy fats.'),
(3, 'PEANUT', 'Peanut Allergy', 'ALLERGEN', 'Severe reaction to peanuts and peanut derivatives.'),
(4, 'SHELLFISH', 'Crustacean & Shellfish Allergy', 'ALLERGEN', 'Avoid crab, shrimp, lobster, and shellfish extracts.'),
(5, 'FISH', 'Fish Allergy', 'ALLERGEN', 'Avoid bony fish, anchovies, bonito, and fish surimi.'),
(6, 'SOY', 'Soy Allergy', 'ALLERGEN', 'Avoid soy lecithin, miso, and soybean derivatives.'),
(7, 'EGG', 'Egg Allergy', 'ALLERGEN', 'Avoid eggs and egg powder.'),
(8, 'HALAL', 'Halal Diet', 'RELIGIOUS', 'Requires Halal-certified ingredients and no pork or alcohol.'),
(9, 'VEGETARIAN', 'Vegetarian Diet', 'DIET', 'Does not consume meat, poultry, or seafood.'),
(10, 'VEGAN', 'Vegan', 'DIET', 'Avoids animal-derived ingredients'),
(11,'LOW_SUGAR', 'Low Sugar', 'DIET', 'Checks sugar per 100 g'),
(12, 'LOW_FAT', 'Low Fat', 'DIET', 'Checks total fat per 100 g'),
(13, 'LOW_TRANS_FAT', 'Low Trans Fat', 'DIET', 'Checks trans fat per 100 g'),
(14, 'LOW_SODIUM', 'Low Sodium', 'DIET', 'Checks sodium per 100 g') ;

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


