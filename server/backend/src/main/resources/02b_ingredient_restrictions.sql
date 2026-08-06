-- ============================================================================
-- INGREDIENT RESTRICTIONS SEED DATA
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
(93, 10, 'CONFLICT', 'Animal/egg byproduct.', 'APPROVED', 'The Vegan Society', 'Additive Breakdown'),
-- 94: E407 (Carrageenan)
(94, 9, 'ALLOWED', 'Seaweed-derived polysaccharides; plant-based gelling agent.', 'APPROVED', 'Vegetarian Society', 'Additive Guide'),
(94, 10, 'ALLOWED', 'Plant-derived alternative to gelatin.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 95: E450 (Sodium Pyrophosphate)
(95, 14, 'CONFLICT', 'High sodium mineral compound.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 96: E250 (Sodium Nitrite)
(96, 14, 'CONFLICT', 'Sodium salt preservative.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 97: E301 (Sodium L-Ascorbate)
(97, 14, 'CONFLICT', 'Sodium-bound antioxidant.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 98: E330 (Citric Acid)
(98, 9, 'ALLOWED', 'Usually produced via microbial fermentation of carbohydrate sources.', 'APPROVED', 'Vegetarian Society', 'Additive Guide'),
(98, 10, 'ALLOWED', 'Plant/fermentation-derived acidulant.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 99: E200 (Sorbic Acid)
(99, 10, 'ALLOWED', 'Synthetic or plant-derived organic compound.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 100: E202 (Potassium Sorbate)
(100, 10, 'ALLOWED', 'Synthetic polyunsaturated fatty acid salt.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 101: E551 (Silicon Dioxide / Anticaking Agent)
(101, 10, 'ALLOWED', 'Inorganic mineral compound.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- ----------------------------------------------------------------------------
-- GRAINS, BAKERY & STARCHES (IDs 102-107)
-- ----------------------------------------------------------------------------
-- 102: Cornflour
(102, 1, 'ALLOWED', 'Naturally gluten-free grain starch.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(102, 10, 'ALLOWED', 'Plant-based starch.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 103: Modified Starch
(103, 1, 'UNCERTAIN', 'Usually derived from corn or tapioca, but can occasionally be derived from wheat.', 'PROPOSED', 'FDA', 'Labeling Regulations'),

-- 104: Tapioca Syrup
(104, 1, 'ALLOWED', 'Naturally gluten-free starch derivative.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(104, 11, 'CONFLICT', 'High concentrated glycemic sweetener.', 'APPROVED', 'ADA', 'Sugar Guidelines'),

-- 105: Wheat Flour
(105, 1, 'CONFLICT', 'Primary gluten source.', 'APPROVED', 'FDA', '21 CFR 101.91 Gluten-Free Labeling'),
(105, 10, 'ALLOWED', 'Plant-based grain flour.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 106: Rice Flour
(106, 1, 'ALLOWED', 'Naturally gluten-free cereal grain flour.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(106, 10, 'ALLOWED', 'Plant-based grain flour.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 107: Corn
(107, 1, 'ALLOWED', 'Naturally gluten-free cereal grain.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(107, 10, 'ALLOWED', 'Plant-based whole food.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- ----------------------------------------------------------------------------
-- MILK & DAIRY (IDs 108-118)
-- ----------------------------------------------------------------------------
-- 108: Cultured Reduced Fat Milk
(108, 2, 'CONFLICT', 'Contains milk proteins and lactose.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(108, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 109: Nonfat Dry Milk
(109, 2, 'CONFLICT', 'Dehydrated skim milk solids.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(109, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 110: Modified Whey
(110, 2, 'CONFLICT', 'Concentrated dairy serum byproduct.', 'APPROVED', 'FDA', 'Milk Protein Allergens'),
(110, 10, 'CONFLICT', 'Animal-derived dairy byproduct.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 111: Nonfat Milk
(111, 2, 'CONFLICT', 'Contains milk proteins and lactose.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(111, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 112: Buttermilk
(112, 2, 'CONFLICT', 'Fermented dairy liquid containing lactose.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(112, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 113: Romano Cheese
(113, 2, 'CONFLICT', 'Hard dairy cheese.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(113, 9, 'UNCERTAIN', 'Traditionally made using animal rennet.', 'PROPOSED', 'Vegetarian Society', 'Cheese & Rennet Criteria'),
(113, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 114: Cheddar Cheese
(114, 2, 'CONFLICT', 'Aged dairy cheese.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(114, 9, 'UNCERTAIN', 'May contain animal rennet or microbial enzymes.', 'PROPOSED', 'Vegetarian Society', 'Cheese & Rennet Criteria'),
(114, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 115: Sour Cream
(115, 2, 'CONFLICT', 'Cultured dairy cream.', 'APPROVED', 'FDA', 'FALCPA Allergen List'),
(115, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 116: Blue Cheese
(116, 2, 'CONFLICT', 'Cultured mold-ripened dairy cheese.', 'APPROVED', 'EFSA', 'Allergen Regulation'),
(116, 9, 'UNCERTAIN', 'May utilize animal rennet during coagulation.', 'PROPOSED', 'Vegetarian Society', 'Cheese & Rennet Criteria'),
(116, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 117: Whey Protein Isolate
(117, 2, 'CONFLICT', 'Highly refined milk protein.', 'APPROVED', 'FDA', 'Milk Protein Allergens'),
(117, 10, 'CONFLICT', 'Animal-derived milk byproduct.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- 118: Milk Protein Concentrate
(118, 2, 'CONFLICT', 'Concentrated dairy proteins.', 'APPROVED', 'FDA', 'Milk Protein Allergens'),
(118, 10, 'CONFLICT', 'Animal-derived dairy product.', 'APPROVED', 'The Vegan Society', 'Dairy Exclusions'),

-- ----------------------------------------------------------------------------
-- NUTS, SEEDS & PRODUCE (IDs 119-133)
-- ----------------------------------------------------------------------------
-- 119: Walnuts
(119, 9, 'ALLOWED', 'Nutritious tree nut.', 'APPROVED', 'Vegetarian Society', 'Plant Foods'),
(119, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 120: Pistachios
(120, 9, 'ALLOWED', 'Nutritious tree nut.', 'APPROVED', 'Vegetarian Society', 'Plant Foods'),
(120, 10, 'ALLOWED', 'Plant-based tree nut.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 121: Dried Cranberries
(121, 10, 'ALLOWED', 'Dehydrated fruit.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),
(121, 11, 'UNCERTAIN', 'Often sweetened with added cane sugar during processing.', 'PROPOSED', 'ADA', 'Added Sugars Guidance'),

-- 122: Golden Raisins
(122, 10, 'ALLOWED', 'Dehydrated fruit.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),
(122, 11, 'CONFLICT', 'Concentrated natural sugars.', 'APPROVED', 'ADA', 'Sugar Guidelines'),

-- 123: Sun-Dried Figs
(123, 10, 'ALLOWED', 'Dehydrated fruit.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 124: Blueberries
(124, 10, 'ALLOWED', 'Whole fresh fruit.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 125: Potatoes
(125, 1, 'ALLOWED', 'Naturally gluten-free tuber.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(125, 10, 'ALLOWED', 'Plant-based produce.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 126: Dried Potatoes
(126, 1, 'ALLOWED', 'Naturally gluten-free dehydrated tuber.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(126, 10, 'ALLOWED', 'Plant-based produce.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 127: Spirulina
(127, 9, 'ALLOWED', 'Blue-green algae / cyanobacteria.', 'APPROVED', 'Vegetarian Society', 'Plant Foods'),
(127, 10, 'ALLOWED', 'Non-animal microbial biomass.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 128: Banana
(128, 10, 'ALLOWED', 'Fresh fruit.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 129: Tomato Juice
(129, 10, 'ALLOWED', 'Fruit juice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 130: Strawberry Puree
(130, 10, 'ALLOWED', 'Processed fruit pulp.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 131: Orange Juice
(131, 10, 'ALLOWED', 'Citrus fruit juice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 132: Lemon Juice Concentrate
(132, 10, 'ALLOWED', 'Concentrated citrus juice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 133: Black Carrot Juice Concentrate
(133, 10, 'ALLOWED', 'Plant-based natural coloring/juice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- ----------------------------------------------------------------------------
-- OILS & FATS (IDs 134-136)
-- ----------------------------------------------------------------------------
-- 134: Soybean Oil
(134, 6, 'CONFLICT', 'Derived from soybeans; highly refined oil may contain residual protein traces.', 'APPROVED', 'FDA', 'Soy Allergen List'),
(134, 10, 'ALLOWED', 'Plant-derived oil.', 'APPROVED', 'The Vegan Society', 'Fats & Oils'),

-- 135: Hydrogenated Coconut Oil
(135, 10, 'ALLOWED', 'Plant-derived oil.', 'APPROVED', 'The Vegan Society', 'Fats & Oils'),
(135, 13, 'CONFLICT', 'Hydrogenation process creates trans-fatty acids.', 'APPROVED', 'WHO', 'Trans Fat Standards'),

-- 136: Medium Chain Triglycerides
(136, 10, 'ALLOWED', 'Usually extracted from coconut or palm oil.', 'APPROVED', 'The Vegan Society', 'Fats & Oils'),

-- ----------------------------------------------------------------------------
-- SPICES, HERBS & AROMATICS (IDs 137-144)
-- ----------------------------------------------------------------------------
-- 137: Onion
(137, 10, 'ALLOWED', 'Allium plant vegetable.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 138: Chili
(138, 10, 'ALLOWED', 'Capsicum fruit/spice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 139: Ground Coriander
(139, 10, 'ALLOWED', 'Plant spice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 140: Red Chillies
(140, 10, 'ALLOWED', 'Capsicum fruit/spice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 141: Ginger
(141, 10, 'ALLOWED', 'Rhizome spice.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 142: Onion Powder
(142, 10, 'ALLOWED', 'Dehydrated ground allium.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 143: Garlic Powder
(143, 10, 'ALLOWED', 'Dehydrated ground allium.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 144: Parsley
(144, 10, 'ALLOWED', 'Fresh or dried herb.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- ----------------------------------------------------------------------------
-- CONDIMENTS & SUGARS (IDs 145-152)
-- ----------------------------------------------------------------------------
-- 145: Apple Cider Vinegar
(145, 10, 'ALLOWED', 'Fermented fruit vinegar.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 146: Vinegar
(146, 10, 'ALLOWED', 'Fermented acid solution.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 147: Distilled Vinegar
(147, 10, 'ALLOWED', 'Fermented grain/spirit vinegar.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 148: Iodized Salt
(148, 14, 'CONFLICT', 'Sodium chloride mineral.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 149: Sea Salt
(149, 14, 'CONFLICT', 'Unrefined sodium chloride.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 150: Corn Syrup
(150, 1, 'ALLOWED', 'Gluten-free starch derivative.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Foods'),
(150, 11, 'CONFLICT', 'High glycemic sweetener.', 'APPROVED', 'ADA', 'Sugar Guidelines'),

-- 151: Cane Sugar
(151, 10, 'UNCERTAIN', 'May be processed using bone char in certain regions.', 'PROPOSED', 'The Vegan Society', 'Sugar Clarification'),
(151, 11, 'CONFLICT', 'Direct sucrose sweetener.', 'APPROVED', 'ADA', 'Sugar Guidelines'),

-- 152: Cocoa Processed with Alkali
(152, 10, 'ALLOWED', 'Dutch-processed plant cocoa powder.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- ----------------------------------------------------------------------------
-- ADDITIVES & CHEMICAL ALIASES (IDs 153-171)
-- ----------------------------------------------------------------------------
-- 153: E341 (Calcium Phosphate)
(153, 10, 'ALLOWED', 'Inorganic mineral salt.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 154: E509 (Calcium Chloride)
(154, 10, 'ALLOWED', 'Inorganic mineral salt.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 155: E917 (Potassium Iodate)
(155, 10, 'ALLOWED', 'Inorganic chemical salt.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 156: E211 (Sodium Benzoate)
(156, 14, 'CONFLICT', 'Sodium-bound organic acid preservative.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 157: E110 (FD&C Yellow No. 6)
(157, 10, 'ALLOWED', 'Synthetic azo dye.', 'APPROVED', 'The Vegan Society', 'Coloring Standards'),

-- 158: E129 (FD&C Red No. 40)
(158, 10, 'ALLOWED', 'Synthetic azo dye.', 'APPROVED', 'The Vegan Society', 'Coloring Standards'),

-- 159: E270 (Lactic Acid)
(159, 2, 'ALLOWED', 'Commercially produced via carbohydrate bacterial fermentation; despite name, does not contain lactose.', 'APPROVED', 'FDA', 'Lactose & Dairy Clarifications'),
(159, 10, 'ALLOWED', 'Bacterial fermentation derived.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 160: E472e (Esters of Mono and Diglycerides)
(160, 8, 'UNCERTAIN', 'Fatty acid esters can be derived from plant oils or animal fats (pork/beef tallow).', 'PROPOSED', 'MUIS', 'Additive Halal Standard'),
(160, 10, 'UNCERTAIN', 'Requires verification of plant vs animal fatty acid source.', 'PROPOSED', 'The Vegan Society', 'Additive Guide'),

-- 161: E300 (Vitamin C / Ascorbic Acid)
(161, 10, 'ALLOWED', 'Synthetic or plant-extracted antioxidant.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 162: E301 (Sodium Ascorbate)
(162, 14, 'CONFLICT', 'Sodium-bound vitamin antioxidant.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 163: E307 (Vitamin E / Alpha Tocopheryl Acetate)
(163, 10, 'ALLOWED', 'Plant oil derived or synthetic antioxidant.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 164: Vitamin A Acetate
(164, 10, 'UNCERTAIN', 'May be synthesized using animal-derived fatty acids or gelatins as carrier media.', 'PROPOSED', 'The Vegan Society', 'Nutrient Carrier Guidelines'),

-- 165: E340ii (Dipotassium Phosphate)
(165, 10, 'ALLOWED', 'Inorganic mineral salt.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 166: E220 (Sulphur Dioxide)
(166, 10, 'ALLOWED', 'Inorganic gas/preservative.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 167: E133 (Brilliant Blue FCF)
(167, 10, 'ALLOWED', 'Synthetic triarylmethane dye.', 'APPROVED', 'The Vegan Society', 'Coloring Standards'),

-- 168: Maltodextrin
(168, 1, 'ALLOWED', 'Gluten-free starch hydrolysate (even when derived from wheat, it is processed below 20ppm).', 'APPROVED', 'Celiac Disease Foundation', 'Maltodextrin Safety'),

-- 169: Dextrose
(169, 1, 'ALLOWED', 'Simple sugar derived from corn or wheat starch; gluten-free.', 'APPROVED', 'Celiac Disease Foundation', 'Gluten-Free Sweeteners'),
(169, 11, 'CONFLICT', 'Direct glucose sweetener.', 'APPROVED', 'ADA', 'Sugar Guidelines'),

-- 170: E331 (Sodium Citrate)
(170, 14, 'CONFLICT', 'Sodium salt of citric acid.', 'APPROVED', 'AHA', 'Sodium Guidelines'),

-- 171: E903 (Carnauba Wax)
(171, 9, 'ALLOWED', 'Extracted from the leaves of the Copernicia prunifera palm.', 'APPROVED', 'Vegetarian Society', 'Plant Waxes'),
(171, 10, 'ALLOWED', 'Plant-derived glazing wax.', 'APPROVED', 'The Vegan Society', 'Plant Waxes'),

-- ----------------------------------------------------------------------------
-- NATURAL EXTRACTS, FLAVORS & OTHERS (IDs 172-178)
-- ----------------------------------------------------------------------------
-- 172: Gelatin
(172, 8, 'UNCERTAIN', 'Requires proof of Halal slaughtered animal collagen source (porcine gelatin is Haram).', 'PROPOSED', 'MUIS', 'Halal Gelatin Policy'),
(172, 9, 'CONFLICT', 'Collagen extracted from animal skin and bones.', 'APPROVED', 'Vegetarian Society', 'Animal Byproducts'),
(172, 10, 'CONFLICT', 'Animal collagen product.', 'APPROVED', 'The Vegan Society', 'Animal Exclusions'),

-- 173: Natural Strawberry Flavor
(173, 10, 'ALLOWED', 'Plant/botanical flavor extraction.', 'APPROVED', 'The Vegan Society', 'Flavor Guidelines'),

-- 174: Fruit and Vegetable Juice Colors
(174, 10, 'ALLOWED', 'Botanical extract colorants.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 175: Artificial Flavor
(175, 10, 'ALLOWED', 'Synthetic chemical aromatics.', 'APPROVED', 'The Vegan Society', 'Additive Guide'),

-- 176: Natural Flavor
(176, 10, 'UNCERTAIN', 'Can legally include extracts from plant OR animal origin.', 'PROPOSED', 'FDA', '21 CFR 101.22 Natural Flavors'),

-- 177: Pectin
(177, 9, 'ALLOWED', 'Structural heteropolysaccharide extracted from citrus fruits or apples.', 'APPROVED', 'Vegetarian Society', 'Plant Gelling Agents'),
(177, 10, 'ALLOWED', 'Plant-derived gelling agent.', 'APPROVED', 'The Vegan Society', 'Plant Foods'),

-- 178: Organic Flavor
(178, 10, 'UNCERTAIN', 'Requires source verification (botanical vs animal organic extract).', 'PROPOSED', 'USDA', 'National Organic Program');