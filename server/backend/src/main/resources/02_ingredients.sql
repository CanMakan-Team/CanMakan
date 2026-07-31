INSERT INTO ingredients (id, ingredient_name, parent_allergen, root_allergen, is_chemical_alias) VALUES
-- Grains & Gluten
(1, 'Whole Grain Oat Flour', 'Gluten Containing Grains', 'GLUTEN', 0),
(2, 'Whole Wheat Flour', 'Gluten Containing Grains', 'GLUTEN', 0),
(3, 'Malted Barley Extract', 'Gluten Containing Grains', 'GLUTEN', 0),
(4, 'Malt Flour (Barley)', 'Gluten Containing Grains', 'GLUTEN', 0),
(5, 'Tapioca Starch', NULL, NULL, 0),
(6, 'Corn Starch', NULL, NULL, 0),
(7, 'Basmati Rice', NULL, NULL, 0),

-- Milk & Dairy
(8, 'Milk Solids', 'Milk Derivatives', 'DAIRY', 0),
(9, 'Milk Fat', 'Milk Derivatives', 'DAIRY', 0),
(10, 'Butter', 'Milk Derivatives', 'DAIRY', 0),
(11, 'Ghee Oil', 'Milk Derivatives', 'DAIRY', 0),
(12, 'Cooking Cream', 'Milk Derivatives', 'DAIRY', 0),
(13, 'Yoghurt Powder', 'Milk Derivatives', 'DAIRY', 0),
(14, 'Sodium Caseinate', 'Milk Derivatives', 'DAIRY', 0),
(15, 'Whey Powder', 'Milk Derivatives', 'DAIRY', 0),
(16, 'Lactose', 'Milk Derivatives', 'DAIRY', 0),
(17, 'Skimmed Milk Powder', 'Milk Derivatives', 'DAIRY', 0),
(18, 'Whole Milk Powder', 'Milk Derivatives', 'DAIRY', 0),

-- Nuts, Seeds & Soy
(19, 'Hazelnut', 'Tree Nuts', 'TREE_NUT', 0),
(20, 'Peanut', 'Peanuts', 'PEANUT', 0),
(21, 'Miso Powder', 'Soy Derivatives', 'SOY', 0),
(22, 'Soya Bean Oil', 'Soy Derivatives', 'SOY', 0),
(23, 'Soy Lecithin', 'Soy Derivatives', 'SOY', 0),

-- Seafood & Shellfish 
(24, 'Bonito', 'Fish', 'FISH', 0),
(25, 'Anchovies', 'Fish', 'FISH', 0),
(26, 'Sardines', 'Fish', 'FISH', 0),
(27, 'Fish Surimi', 'Fish Derivatives', 'FISH', 0),
(28, 'Crab Stick (Fish Surimi)', 'Imitation Seafood', 'FISH', 0),
(29, 'Crab Meat', 'Crustaceans', 'SHELLFISH', 0),
(30, 'Crab Flavour', 'Crustaceans', 'SHELLFISH', 0),

-- Eggs
(31, 'Egg Powder', 'Eggs', 'EGG', 0),

-- Oils & Fats
(32, 'Palm Oil', 'Vegetable Oils', NULL, 0),
(33, 'Hydrogenated Rapeseed Oil', 'Vegetable Oils', NULL, 0),
(34, 'Shortening', 'Fats', NULL, 0),

-- Food Additives & E-Numbers (Chemical Aliases)
(35, 'E500(ii) (Sodium Bicarbonate)', 'Acidity Regulators', 'ADDITIVE', 1),
(36, 'E307b (Tocopherols Concentrate)', 'Antioxidants', 'ADDITIVE', 1),
(37, 'E1420 (Starch Acetate)', 'Thickeners', 'ADDITIVE', 1),
(38, 'E150c (Ammonia Caramel)', 'Food Colorings', 'ADDITIVE', 1),
(39, 'E102 (Tartrazine)', 'Food Colorings', 'ADDITIVE', 1),
(40, 'E110 (Sunset Yellow)', 'Food Colorings', 'ADDITIVE', 1),
(41, 'E124 (Ponceau 4R)', 'Food Colorings', 'ADDITIVE', 1),
(42, 'E1441 (Hydroxypropyl Distarch Phosphate)', 'Thickeners', 'ADDITIVE', 1),
(43, 'E466 (Carboxymethyl Cellulose)', 'Stabilizers', 'ADDITIVE', 1),
(44, 'E471 (Mono- and Diglycerides)', 'Emulsifiers', 'ADDITIVE', 1),
(45, 'E452i (Sodium Polyphosphate)', 'Stabilizers', 'ADDITIVE', 1),
(46, 'E473 (Sucrose Esters of Fatty Acids)', 'Emulsifiers', 'ADDITIVE', 1),
(47, 'E469 (Enzymatically Hydrolysed CMC)', 'Emulsifiers', 'ADDITIVE', 1),
(48, 'E635 (Disodium 5''-Ribonucleotides)', 'Flavor Enhancers', 'ADDITIVE', 1),
(49, 'E627 (Disodium Guanylate)', 'Flavor Enhancers', 'ADDITIVE', 1),
(50, 'E631 (Disodium Inosinate)', 'Flavor Enhancers', 'ADDITIVE', 1),
(51, 'INS 150a (Plain Caramel)', 'Food Colorings', 'ADDITIVE', 1),
(52, 'E621 (Monosodium Glutamate)', 'Flavor Enhancers', 'ADDITIVE', 1),

-- General Base Ingredients
(53, 'Tomato Paste', NULL, NULL, 0),
(54, 'Xanthan Gum', 'Thickeners', 'ADDITIVE', 0),
(55, 'Cocoa Powder', NULL, NULL, 0),
(56, 'Dried Seaweed', NULL, NULL, 0),
(57, 'Mango', NULL, NULL, 0),
(58, 'Salt', NULL, NULL, 0),
(59, 'Sugar', NULL, NULL, 0);