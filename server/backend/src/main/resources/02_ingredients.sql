INSERT IGNORE INTO ingredients (id, ingredient_name, parent_allergen, root_allergen, is_chemical_alias) VALUES
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
(48, 'E635 (Disodium 5-Ribonucleotides)', 'Flavor Enhancers', 'ADDITIVE', 1),
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
(59, 'Sugar', NULL, NULL, 0),

-- Meat, Poultry & Exotic Proteins
(60, 'Pork', 'Meat', 'MEAT', 0),
(61, 'Chicken', 'Poultry', 'MEAT', 0),
(62, 'Kangaroo Meat', 'Exotic Meat', 'MEAT', 0),
(63, 'Crocodile Meat', 'Exotic Meat', 'MEAT', 0),

-- Seafood & Fish
(64, 'Cod', 'Fish', 'FISH', 0),

-- Specialty Nuts, Seeds & Produce
(65, 'Cashew Nuts', 'Tree Nuts', 'TREE_NUT', 0),
(66, 'Pine Nut Kernels', 'Tree Nuts', 'TREE_NUT', 0),
(67, 'Macadamias', 'Tree Nuts', 'TREE_NUT', 0),
(68, 'Almonds', 'Tree Nuts', 'TREE_NUT', 0),
(69, 'Soybean', 'Soy Derivatives', 'SOY', 0),
(70, 'Soy Protein', 'Soy Derivatives', 'SOY', 0),
(71, 'Sunflower Seed Oil', 'Vegetable Oils', NULL, 0),
(72, 'Canola Oil', 'Vegetable Oils', NULL, 0),

-- Dairy & Fermented Derivatives
(73, 'Hard Cheese', 'Milk Derivatives', 'DAIRY', 0),
(74, 'Pecorino Cheese (Sheep Milk)', 'Milk Derivatives', 'DAIRY', 0),
(75, 'Cream Cheese', 'Milk Derivatives', 'DAIRY', 0),
(76, 'Curd', 'Milk Derivatives', 'DAIRY', 0),

-- Grains, Bakery & Starches
(77, 'Barley Malt', 'Gluten Containing Grains', 'GLUTEN', 0),
(78, 'Wheat Starch', 'Gluten Containing Grains', 'GLUTEN', 0),
(79, 'Wheat Protein', 'Gluten Containing Grains', 'GLUTEN', 0),
(80, 'Wheat Fibre', 'Gluten Containing Grains', 'GLUTEN', 0),
(81, 'Potato Starch / Flakes', NULL, NULL, 0),
(82, 'Breadcrumb Coating', 'Gluten Containing Grains', 'GLUTEN', 0),

-- Spices, Herbs & Aromatics
(83, 'Basil', NULL, NULL, 0),
(84, 'Cayenne Pepper', NULL, NULL, 0),
(85, 'Chives', NULL, NULL, 0),
(86, 'Garlic', NULL, NULL, 0),
(87, 'Paprika', NULL, NULL, 0),
(88, 'Turmeric', NULL, NULL, 0),

-- Beverages, Sweets & Natural Extracts
(89, 'Honey', NULL, NULL, 0),
(90, 'Coffee', NULL, NULL, 0),
(91, 'Mandarin Orange Juice Concentrate', NULL, NULL, 0),
(92, 'Longan Red Date Extract', NULL, NULL, 0),

-- Additives, Preservatives & E-Numbers
(93, 'E1105 (Lysozyme from eggs)', 'Egg Derivatives', 'EGG', 1),
(94, 'E407 (Carrageenan)', 'Thickeners', 'ADDITIVE', 1),
(95, 'E450 (Sodium Pyrophosphate)', 'Stabilizers', 'ADDITIVE', 1),
(96, 'E250 (Sodium Nitrite)', 'Preservatives', 'ADDITIVE', 1),
(97, 'E301 (Sodium L-Ascorbate)', 'Antioxidants', 'ADDITIVE', 1),
(98, 'E330 (Citric Acid)', 'Acidity Regulators', 'ADDITIVE', 1),
(99, 'E200 (Sorbic Acid)', 'Preservatives', 'ADDITIVE', 1),
(100, 'E202 (Potassium Sorbate)', 'Preservatives', 'ADDITIVE', 1),
(101, 'E551 (Silicon Dioxide / Anticaking Agent)', 'Anticaking Agents', 'ADDITIVE', 1),

-- Grains, Bakery & Starches
(102, 'Cornflour', 'Corn Derivatives', NULL, 0),
(103, 'Modified Starch', 'Thickeners', 'ADDITIVE', 0),
(104, 'Tapioca Syrup', NULL, NULL, 0),
(105, 'Wheat Flour', 'Gluten Containing Grains', 'GLUTEN', 0),
(106, 'Rice Flour', NULL, NULL, 0),
(107, 'Corn', 'Corn Derivatives', NULL, 0),

-- Milk & Dairy
(108, 'Cultured Reduced Fat Milk', 'Milk Derivatives', 'DAIRY', 0),
(109, 'Nonfat Dry Milk', 'Milk Derivatives', 'DAIRY', 0),
(110, 'Modified Whey', 'Milk Derivatives', 'DAIRY', 0),
(111, 'Nonfat Milk', 'Milk Derivatives', 'DAIRY', 0),
(112, 'Buttermilk', 'Milk Derivatives', 'DAIRY', 0),
(113, 'Romano Cheese', 'Milk Derivatives', 'DAIRY', 0),
(114, 'Cheddar Cheese', 'Milk Derivatives', 'DAIRY', 0),
(115, 'Sour Cream', 'Milk Derivatives', 'DAIRY', 0),
(116, 'Blue Cheese', 'Milk Derivatives', 'DAIRY', 0),
(117, 'Whey Protein Isolate', 'Milk Derivatives', 'DAIRY', 0),
(118, 'Milk Protein Concentrate', 'Milk Derivatives', 'DAIRY', 0),

-- Nuts, Seeds & Produce
(119, 'Walnuts', 'Tree Nuts', 'TREE_NUT', 0),
(120, 'Pistachios', 'Tree Nuts', 'TREE_NUT', 0),
(121, 'Dried Cranberries', NULL, NULL, 0),
(122, 'Golden Raisins', NULL, NULL, 0),
(123, 'Sun-Dried Figs', NULL, NULL, 0),
(124, 'Blueberries', NULL, NULL, 0),
(125, 'Potatoes', NULL, NULL, 0),
(126, 'Dried Potatoes', NULL, NULL, 0),
(127, 'Spirulina', NULL, NULL, 0),
(128, 'Banana', NULL, NULL, 0),
(129, 'Tomato Juice', NULL, NULL, 0),
(130, 'Strawberry Puree', NULL, NULL, 0),
(131, 'Orange Juice', NULL, NULL, 0),
(132, 'Lemon Juice Concentrate', NULL, NULL, 0),
(133, 'Black Carrot Juice Concentrate', NULL, NULL, 0),

-- Oils & Fats
(134, 'Soybean Oil', 'Soy Derivatives', 'SOY', 0),
(135, 'Hydrogenated Coconut Oil', 'Vegetable Oils', NULL, 0),
(136, 'Medium Chain Triglycerides', 'Vegetable Oils', NULL, 0),

-- Spices, Herbs & Aromatics
(137, 'Onion', NULL, NULL, 0),
(138, 'Chili', NULL, NULL, 0),
(139, 'Ground Coriander', NULL, NULL, 0),
(140, 'Red Chillies', NULL, NULL, 0),
(141, 'Ginger', NULL, NULL, 0),
(142, 'Onion Powder', NULL, NULL, 0),
(143, 'Garlic Powder', NULL, NULL, 0),
(144, 'Parsley', NULL, NULL, 0),

-- Condiments & Sugars
(145, 'Apple Cider Vinegar', NULL, NULL, 0),
(146, 'Vinegar', NULL, NULL, 0),
(147, 'Distilled Vinegar', NULL, NULL, 0),
(148, 'Iodized Salt', NULL, NULL, 0),
(149, 'Sea Salt', NULL, NULL, 0),
(150, 'Corn Syrup', 'Corn Derivatives', NULL, 0),
(151, 'Cane Sugar', NULL, NULL, 0),
(152, 'Cocoa Processed with Alkali', NULL, NULL, 0),

-- Food Additives & E-Numbers (Chemical Aliases)
(153, 'E341 (Calcium Phosphate)', 'Acidity Regulators', 'ADDITIVE', 1),
(154, 'E509 (Calcium Chloride)', 'Firming Agents', 'ADDITIVE', 1),
(155, 'E917 (Potassium Iodate)', 'Preservatives', 'ADDITIVE', 1),
(156, 'E211 (Sodium Benzoate)', 'Preservatives', 'ADDITIVE', 1),
(157, 'E110 (FD&C Yellow No. 6)', 'Food Colorings', 'ADDITIVE', 1),
(158, 'E129 (FD&C Red No. 40)', 'Food Colorings', 'ADDITIVE', 1),
(159, 'E270 (Lactic Acid)', 'Acidity Regulators', 'ADDITIVE', 1),
(160, 'E472e (Esters of Mono and Diglycerides)', 'Emulsifiers', 'ADDITIVE', 1),
(161, 'E300 (Vitamin C / Ascorbic Acid)', 'Antioxidants', 'ADDITIVE', 1),
(162, 'E301 (Sodium Ascorbate)', 'Antioxidants', 'ADDITIVE', 1),
(163, 'E307 (Vitamin E / Alpha Tocopheryl Acetate)', 'Antioxidants', 'ADDITIVE', 1),
(164, 'Vitamin A Acetate', 'Nutrient Supplements', 'ADDITIVE', 1),
(165, 'E340ii (Dipotassium Phosphate)', 'Stabilizers', 'ADDITIVE', 1),
(166, 'E220 (Sulphur Dioxide)', 'Preservatives', 'ADDITIVE', 1),
(167, 'E133 (Brilliant Blue FCF)', 'Food Colorings', 'ADDITIVE', 1),
(168, 'Maltodextrin', 'Thickeners', 'ADDITIVE', 1),
(169, 'Dextrose', 'Sweeteners', 'ADDITIVE', 1),
(170, 'E331 (Sodium Citrate)', 'Acidity Regulators', 'ADDITIVE', 1),
(171, 'E903 (Carnauba Wax)', 'Glazing Agents', 'ADDITIVE', 1),

-- Natural Extracts, Flavors & Others
(172, 'Gelatin', 'Gelatin', NULL, 0),
(173, 'Natural Strawberry Flavor', 'Flavorings', 'ADDITIVE', 0),
(174, 'Fruit and Vegetable Juice Colors', 'Food Colorings', 'ADDITIVE', 0),
(175, 'Artificial Flavor', 'Flavorings', 'ADDITIVE', 0),
(176, 'Natural Flavor', 'Flavorings', 'ADDITIVE', 0),
(177, 'Pectin', 'Thickeners', 'ADDITIVE', 0),
(178, 'Organic Flavor', 'Flavorings', 'ADDITIVE', 0);
-- ============================================================================
-- Common allergen labels missing from the base seed (HY): plain milk / wheat
-- forms that appear on real product labels but were previously UNRESOLVED.
-- Matching is exact-after-normalize, so each real-world spelling is its own row.
-- ============================================================================
INSERT IGNORE INTO ingredients (id, ingredient_name, parent_allergen, root_allergen, is_chemical_alias) VALUES
-- Milk & Dairy -> DAIRY
(179, 'Milk', 'Milk Derivatives', 'DAIRY', 0),
(180, 'Fresh Milk', 'Milk Derivatives', 'DAIRY', 0),
(181, 'Full Cream Milk', 'Milk Derivatives', 'DAIRY', 0),
(182, 'Full Cream Milk Powder', 'Milk Derivatives', 'DAIRY', 0),
(183, 'Whole Milk', 'Milk Derivatives', 'DAIRY', 0),
(184, 'Skim Milk', 'Milk Derivatives', 'DAIRY', 0),
(185, 'Skimmed Milk', 'Milk Derivatives', 'DAIRY', 0),
(186, 'Low Fat Milk', 'Milk Derivatives', 'DAIRY', 0),
(187, 'Reduced Fat Milk', 'Milk Derivatives', 'DAIRY', 0),
(188, 'UHT Milk', 'Milk Derivatives', 'DAIRY', 0),
(189, 'Pasteurised Milk', 'Milk Derivatives', 'DAIRY', 0),
(190, 'Milk Powder', 'Milk Derivatives', 'DAIRY', 0),
(191, 'Evaporated Milk', 'Milk Derivatives', 'DAIRY', 0),
(192, 'Condensed Milk', 'Milk Derivatives', 'DAIRY', 0),
(193, 'Sweetened Condensed Milk', 'Milk Derivatives', 'DAIRY', 0),
(194, 'Cream', 'Milk Derivatives', 'DAIRY', 0),
(195, 'Fresh Cream', 'Milk Derivatives', 'DAIRY', 0),
(196, 'Dairy Cream', 'Milk Derivatives', 'DAIRY', 0),
(197, 'Cheese', 'Milk Derivatives', 'DAIRY', 0),
(198, 'Mozzarella Cheese', 'Milk Derivatives', 'DAIRY', 0),
(199, 'Cheddar Cheese', 'Milk Derivatives', 'DAIRY', 0),
(200, 'Parmesan Cheese', 'Milk Derivatives', 'DAIRY', 0),
(201, 'Pecorino Cheese', 'Milk Derivatives', 'DAIRY', 0),
(202, 'Casein', 'Milk Derivatives', 'DAIRY', 0),
(203, 'Milk Protein', 'Milk Derivatives', 'DAIRY', 0),
(204, 'Whey Protein', 'Milk Derivatives', 'DAIRY', 0),
-- Wheat & Gluten -> GLUTEN
(206, 'Wheat', 'Gluten Containing Grains', 'GLUTEN', 0),
(207, 'Durum Wheat', 'Gluten Containing Grains', 'GLUTEN', 0),
(208, 'Semolina', 'Gluten Containing Grains', 'GLUTEN', 0),
(209, 'Wheat Semolina', 'Gluten Containing Grains', 'GLUTEN', 0),
(210, 'Durum Wheat Semolina', 'Gluten Containing Grains', 'GLUTEN', 0),
(211, 'Spelt', 'Gluten Containing Grains', 'GLUTEN', 0),
(212, 'Rye', 'Gluten Containing Grains', 'GLUTEN', 0),
(213, 'Barley', 'Gluten Containing Grains', 'GLUTEN', 0),
-- Common non-allergen labels -> known-safe (catalog hit, no root allergen)
(214, 'Olive Oil', NULL, NULL, 0),
(215, 'Extra Virgin Olive Oil', NULL, NULL, 0),
(216, 'Salt', NULL, NULL, 0),
(217, 'Sea Salt', NULL, NULL, 0),
(218, 'Sugar', NULL, NULL, 0),
(219, 'Water', NULL, NULL, 0),
(220, 'Basil', NULL, NULL, 0),
(221, 'Natural Basil Flavour', NULL, NULL, 0);
