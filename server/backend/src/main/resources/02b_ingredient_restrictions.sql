-- ============================================================================
-- INGREDIENT RESTRICTIONS SEED DATA
-- Mapped using ingredient_id (1-59) and dietary_restriction_id (1-10)
-- ============================================================================

INSERT INTO ingredient_restrictions (
    ingredient_id, 
    dietary_restriction_id, 
    rule_effect, 
    reason, 
    review_status, 
    source_name, 
    source_reference
) VALUES
-- ----------------------------------------------------------------------------
-- GRAINS & GLUTEN (IDs 1-4)
-- ----------------------------------------------------------------------------
-- 1: Whole Grain Oat Flour
(1, 1, 'UNCERTAIN', 'Oats contain avenin and are prone to cross-contamination unless certified gluten-free.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),
(1, 10, 'ALLOWED', 'Plant-based oat ingredient.', 'APPROVED', 'The Vegan Society', 'Vegan Ingredient Checker'),

-- 2: Whole Wheat Flour
(2, 1, 'CONFLICT', 'Contains wheat gluten protein.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),
(2, 10, 'ALLOWED', 'Plant-based wheat flour.', 'APPROVED', 'The Vegan Society', 'Vegan Ingredient Checker'),

-- 3: Malted Barley Extract
(3, 1, 'CONFLICT', 'Barley is a direct source of gluten.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Diet Guidelines'),
(3, 8, 'UNCERTAIN', 'Malt extraction process may involve trace fermentation; requires verification.', 'PROPOSED', 'MUIS', 'Halal Guidelines'),

-- 4: Malt Flour (Barley)
(4, 1, 'CONFLICT', 'Barley flour contains gluten.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Diet Guidelines'),

-- ----------------------------------------------------------------------------
-- MILK & DAIRY (IDs 8-18)
-- ----------------------------------------------------------------------------
-- 8: Milk Solids
(8, 2, 'CONFLICT', 'Contains milk solids and lactose.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(8, 9, 'ALLOWED', 'Permissible for Lacto-Vegetarians.', 'APPROVED', 'Vegetarian Society', 'Dairy Guidelines'),
(8, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 9: Milk Fat
(9, 2, 'CONFLICT', 'Milk fat derivative.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(9, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 10: Butter
(10, 2, 'CONFLICT', 'Made from milk cream; contains dairy allergens and lactose.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(10, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 11: Ghee Oil
(11, 2, 'CONFLICT', 'Clarified butter oil; derived from milk.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(11, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 12: Cooking Cream
(12, 2, 'CONFLICT', 'Dairy cream containing high lactose and dairy protein.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(12, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 13: Yoghurt Powder
(13, 2, 'CONFLICT', 'Dehydrated cultured milk; triggers dairy intolerance.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(13, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 14: Sodium Caseinate
(14, 2, 'CONFLICT', 'Milk protein derivative.', 'APPROVED', 'FDA', 'Milk Protein Allergens'),
(14, 10, 'CONFLICT', 'Animal-derived dairy protein.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 15: Whey Powder
(15, 2, 'CONFLICT', 'High-lactose milk serum byproduct.', 'APPROVED', 'FDA', 'Milk Protein Allergens'),
(15, 10, 'CONFLICT', 'Animal-derived dairy byproduct.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 16: Lactose
(16, 2, 'CONFLICT', 'Milk sugar; primary cause of dairy intolerance.', 'APPROVED', 'NIH', 'Lactose Intolerance Standards'),
(16, 10, 'CONFLICT', 'Extracted from milk.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 17: Skimmed Milk Powder
(17, 2, 'CONFLICT', 'Concentrated dairy solids.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(17, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 18: Whole Milk Powder
(18, 2, 'CONFLICT', 'Dehydrated whole milk.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(18, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- ----------------------------------------------------------------------------
-- NUTS, SEEDS & SOY (IDs 19-23)
-- ----------------------------------------------------------------------------
-- 19: Hazelnut
(19, 9, 'ALLOWED', 'Nutritious plant-based tree nut.', 'APPROVED', 'Vegetarian Society', 'Plant Foods'),
(19, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 20: Peanut
(20, 3, 'CONFLICT', 'Primary peanut allergen causing severe reactions.', 'APPROVED', 'FDA', 'Major Food Allergens'),
(20, 10, 'ALLOWED', 'Legume / plant-based protein.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 21: Miso Powder
(21, 6, 'CONFLICT', 'Fermented soy paste product.', 'APPROVED', 'FDA', 'Soy Allergen List'),

-- 22: Soya Bean Oil
(22, 6, 'CONFLICT', 'Derived from soybeans; may contain trace soy proteins depending on refining level.', 'APPROVED', 'FDA', 'Soy Allergen List'),

-- 23: Soy Lecithin
(23, 6, 'CONFLICT', 'Soy-derived emulsifier.', 'APPROVED', 'FDA', 'Soy Allergen List'),
(23, 10, 'ALLOWED', 'Plant-derived lecithin.', 'APPROVED', 'The Vegan Society', 'Emulsifiers'),

-- ----------------------------------------------------------------------------
-- SEAFOOD & SHELLFISH (IDs 24-30)
-- ----------------------------------------------------------------------------
-- 24: Bonito
(24, 5, 'CONFLICT', 'Dried skipjack tuna fish ingredient.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(24, 9, 'CONFLICT', 'Fish product; not vegetarian.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(24, 10, 'CONFLICT', 'Animal-derived ingredient.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 25: Anchovies
(25, 5, 'CONFLICT', 'Bony fish allergen.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(25, 9, 'CONFLICT', 'Fish product; not vegetarian.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(25, 10, 'CONFLICT', 'Animal-derived ingredient.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 26: Sardines
(26, 5, 'CONFLICT', 'Small oily fish allergen.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(26, 9, 'CONFLICT', 'Fish product; not vegetarian.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(26, 10, 'CONFLICT', 'Animal-derived ingredient.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 27: Fish Surimi
(27, 5, 'CONFLICT', 'Processed fish protein paste.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(27, 9, 'CONFLICT', 'Fish product; not vegetarian.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(27, 10, 'CONFLICT', 'Animal-derived ingredient.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 28: Crab Stick (Fish Surimi)
(28, 5, 'CONFLICT', 'Imitation crab made from processed fish surimi.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(28, 4, 'UNCERTAIN', 'May contain crab extract or shellfish flavoring for taste.', 'PROPOSED', 'FARRP', 'Cross-Allergen Guidance'),
(28, 9, 'CONFLICT', 'Contains fish/seafood.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(28, 10, 'CONFLICT', 'Animal-derived product.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 29: Crab Meat
(29, 4, 'CONFLICT', 'Direct crustacean shellfish allergen.', 'APPROVED', 'FDA', 'Shellfish Allergen List'),
(29, 8, 'UNCERTAIN', 'Requires verification under specific school of jurisprudence regarding sea creatures.', 'PROPOSED', 'MUIS', 'Seafood Classification'),
(29, 9, 'CONFLICT', 'Crustacean meat.', 'APPROVED', 'Vegetarian Society', 'Dietary Standards'),
(29, 10, 'CONFLICT', 'Animal-derived product.', 'APPROVED', 'The Vegan Society', 'Animal Product List'),

-- 30: Crab Flavour
(30, 4, 'UNCERTAIN', 'Can be natural (derived from actual crab) or artificial.', 'PROPOSED', 'FDA', 'Flavorings Guidelines'),

-- ----------------------------------------------------------------------------
-- EGGS (ID 31)
-- ----------------------------------------------------------------------------
-- 31: Egg Powder
(31, 7, 'CONFLICT', 'Dehydrated egg allergen.', 'APPROVED', 'FDA', 'Egg Allergen List'),
(31, 10, 'CONFLICT', 'Animal byproduct.', 'APPROVED', 'The Vegan Society', 'Egg Exclusions'),

-- ----------------------------------------------------------------------------
-- OILS & FATS (IDs 32-34)
-- ----------------------------------------------------------------------------
-- 34: Shortening
(34, 8, 'UNCERTAIN', 'May be plant-based or contain lard/animal fat.', 'PROPOSED', 'MUIS', 'Fats & Oils Standard'),
(34, 9, 'UNCERTAIN', 'Requires verification if animal tallow or vegetable fat was used.', 'PROPOSED', 'Vegetarian Society', 'Fat Derivatives'),
(34, 10, 'UNCERTAIN', 'Requires source verification for animal fats.', 'PROPOSED', 'The Vegan Society', 'Fat Derivatives'),

-- ----------------------------------------------------------------------------
-- FOOD ADDITIVES & E-NUMBERS (IDs 35-52)
-- ----------------------------------------------------------------------------
-- 44: E471 (Mono- and Diglycerides)
(44, 8, 'UNCERTAIN', 'May be derived from plant or animal fats (e.g., pork tallow).', 'APPROVED', 'MUIS', 'E-Number Guidance'),
(44, 9, 'UNCERTAIN', 'Source-dependent (vegetable oil vs animal fat).', 'PROPOSED', 'Vegetarian Society', 'E-Number Guide'),
(44, 10, 'UNCERTAIN', 'Source-dependent (vegetable oil vs animal fat).', 'PROPOSED', 'The Vegan Society', 'E-Number Guide'),

-- 46: E473 (Sucrose Esters of Fatty Acids)
(46, 8, 'UNCERTAIN', 'Fatty acid portion may be animal-derived.', 'PROPOSED', 'JAKIM', 'Additives List'),
(46, 10, 'UNCERTAIN', 'Fatty acid portion may be animal-derived.', 'PROPOSED', 'The Vegan Society', 'Additives List'),

-- 48: E635 (Disodium 5'-Ribonucleotides)
(48, 8, 'UNCERTAIN', 'Often produced using tapioca/corn, but can involve animal tissue derivatives.', 'PROPOSED', 'MUIS', 'Flavor Enhancers'),

-- 50: E631 (Disodium Inosinate)
(50, 8, 'UNCERTAIN', 'Often derived from meat or fish (sardines/pork) or bacterial fermentation.', 'PROPOSED', 'MUIS', 'Additives Breakdown'),
(50, 9, 'UNCERTAIN', 'May be animal/fish derived.', 'PROPOSED', 'Vegetarian Society', 'Additive Guide'),
(50, 10, 'UNCERTAIN', 'May be animal/fish derived.', 'PROPOSED', 'The Vegan Society', 'Additive Guide'),

-- ----------------------------------------------------------------------------
-- MEAT, POULTRY & EXOTIC PROTEINS (IDs 60-63)
-- ----------------------------------------------------------------------------
-- 60: Pork
(60, 8, 'CONFLICT', 'Pork is strictly forbidden (Haram) in Islamic dietary law.', 'APPROVED', 'MUIS', 'General Guidelines for Halal Certification'),
(60, 9, 'CONFLICT', 'Animal meat; non-vegetarian.', 'APPROVED', 'Vegetarian Society', 'Meat Definition'),
(60, 10, 'CONFLICT', 'Animal meat; non-vegan.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- 61: Chicken
(61, 8, 'UNCERTAIN', 'Requires Halal slaughter certification.', 'PROPOSED', 'MUIS', 'Halal Certification Standards'),
(61, 9, 'CONFLICT', 'Poultry meat; non-vegetarian.', 'APPROVED', 'Vegetarian Society', 'Poultry Definition'),
(61, 10, 'CONFLICT', 'Animal meat; non-vegan.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- 62: Kangaroo Meat
(62, 8, 'UNCERTAIN', 'Requires Halal slaughter verification.', 'PROPOSED', 'MUIS', 'Halal Certification Standards'),
(62, 9, 'CONFLICT', 'Animal meat; non-vegetarian.', 'APPROVED', 'Vegetarian Society', 'Meat Definition'),
(62, 10, 'CONFLICT', 'Animal meat; non-vegan.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- 63: Crocodile Meat
(63, 8, 'CONFLICT', 'Predatory reptile meat is non-Halal.', 'APPROVED', 'MUIS', 'Halal Ruling on Predatory Animals'),
(63, 9, 'CONFLICT', 'Animal meat; non-vegetarian.', 'APPROVED', 'Vegetarian Society', 'Meat Definition'),
(63, 10, 'CONFLICT', 'Animal meat; non-vegan.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- ----------------------------------------------------------------------------
-- SEAFOOD & FISH (ID 64)
-- ----------------------------------------------------------------------------
-- 64: Cod
(64, 5, 'CONFLICT', 'Cod is a bony fish allergen.', 'APPROVED', 'FDA', 'Fish Allergen Guidance'),
(64, 9, 'CONFLICT', 'Seafood; non-vegetarian.', 'APPROVED', 'Vegetarian Society', 'Seafood Exclusions'),
(64, 10, 'CONFLICT', 'Seafood; non-vegan.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- ----------------------------------------------------------------------------
-- SPECIALTY NUTS, SEEDS & PRODUCE (IDs 65-70)
-- ----------------------------------------------------------------------------
-- 65: Cashew Nuts
(65, 9, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'Vegetarian Society', 'Nutrient Standards'),
(65, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 66: Pine Nut Kernels
(66, 9, 'ALLOWED', 'Plant-based seed/nut.', 'APPROVED', 'Vegetarian Society', 'Nutrient Standards'),
(66, 10, 'ALLOWED', 'Plant-based seed/nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 67: Macadamias
(67, 9, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'Vegetarian Society', 'Nutrient Standards'),
(67, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 68: Almonds
(68, 9, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'Vegetarian Society', 'Nutrient Standards'),
(68, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 69: Soybean
(69, 6, 'CONFLICT', 'Direct soy allergen source.', 'APPROVED', 'FDA', 'FALCPA Soy Guidelines'),
(69, 10, 'ALLOWED', 'Plant-based protein source.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 70: Soy Protein
(70, 6, 'CONFLICT', 'Concentrated soy allergen.', 'APPROVED', 'FDA', 'FALCPA Soy Guidelines'),
(70, 10, 'ALLOWED', 'Plant-based protein source.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- ----------------------------------------------------------------------------
-- DAIRY & FERMENTED DERIVATIVES (IDs 73-76)
-- ----------------------------------------------------------------------------
-- 73: Hard Cheese
(73, 2, 'CONFLICT', 'Contains dairy proteins and lactose.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(73, 9, 'UNCERTAIN', 'May contain animal rennet from stomach lining.', 'PROPOSED', 'Vegetarian Society', 'Cheese & Rennet Criteria'),
(73, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 74: Pecorino Cheese (Sheep Milk)
(74, 2, 'CONFLICT', 'Dairy product containing sheep milk proteins.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(74, 9, 'UNCERTAIN', 'Traditional recipes use animal rennet.', 'PROPOSED', 'Vegetarian Society', 'Cheese & Rennet Criteria'),
(74, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 75: Cream Cheese
(75, 2, 'CONFLICT', 'High lactose and dairy protein content.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(75, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 76: Curd
(76, 2, 'CONFLICT', 'Coagulated milk product containing lactose.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(76, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- ----------------------------------------------------------------------------
-- GRAINS, BAKERY & STARCHES (IDs 77-80, 82)
-- ----------------------------------------------------------------------------
-- 77: Barley Malt
(77, 1, 'CONFLICT', 'Barley is a primary source of gluten.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Diet Guidelines'),

-- 78: Wheat Starch
(78, 1, 'CONFLICT', 'Derived from wheat; contains gluten unless specially processed below 20ppm.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),

-- 79: Wheat Protein
(79, 1, 'CONFLICT', 'Concentrated wheat gluten protein.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),

-- 80: Wheat Fibre
(80, 1, 'CONFLICT', 'Contains wheat gluten traces.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),

-- 82: Breadcrumb Coating
(82, 1, 'CONFLICT', 'Usually made from wheat flour containing gluten.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Diet Guidelines'),

-- ----------------------------------------------------------------------------
-- BEVERAGES, SWEETS & ADDITIVES (IDs 89, 93)
-- ----------------------------------------------------------------------------
-- 89: Honey
(89, 10, 'CONFLICT', 'Produced by bees; excluded from vegan diets.', 'APPROVED', 'The Vegan Society', 'Definition of Veganism'),

-- 93: E1105 (Lysozyme from eggs)
(93, 7, 'CONFLICT', 'Preservative enzyme extracted directly from egg whites.', 'APPROVED', 'EFSA', 'Egg Derivatives Directive'),
(93, 10, 'CONFLICT', 'Animal/egg byproduct.', 'APPROVED', 'The Vegan Society', 'Additive Breakdown');