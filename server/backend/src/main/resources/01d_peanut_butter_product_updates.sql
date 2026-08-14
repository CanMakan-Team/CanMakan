-- Overlay peanut-butter / spread catalog fields from csv_Update_peanut_butter_product.csv.
-- Merged into 01_products.sql INSERT rows; kept for reference only (not loaded at startup).
-- CSV rows: 41. Matched: 39. Missing: 2.

-- Missing barcodes (not updated): 0051500255308, 8858914400155

UPDATE products
SET
    category_tags = 'en:sauces',
    main_category_en = 'Sauces',
    ingredients_text = 'Water, sugar, soy sauce (water, soybeans, salt, corn starch), salt, modified corn starch, yeast extract (yeast extract, salt, water), caramel color, dried mushrooms.',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = 'en:no-gluten,en:no-gmos,en:certified-gluten-free,en:non-gmo-project',
    labels_en = 'No gluten,No GMOs,Certified gluten-free,Non GMO project'
WHERE barcode = '0078895152258';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '0797776416745';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '0806809032033';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '0806809032125';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Peanuts, Sustainable Palm Oil, Organic Sugar, Sea Salt.',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = 'en:sustainable,en:vegetarian,en:kosher,en:sustainable-palm-oil,en:vegan',
    labels_en = 'Sustainable,Vegetarian,Kosher,Sustainable Palm Oil,Vegan'
WHERE barcode = '0850060027017';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '1111111667729';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '13216850';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Roasted Peanuts, Brown Cane Sugar, Cocoa Butter, Sea Salt',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '3608580141082';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = NULL,
    ingredients_text = 'Sugar, Rapeseed Oil, Hazelnus (13%), Palm Oil, Sweet Whey Powder (From Milk), Fat-Reduced Cocoa Powder (6%), Skimmed Milk Powder, Emulsifier (Sunflower Lecithins, Flavoring)',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '4002309015392';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8834000182860';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8886460301299';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = NULL,
    ingredients_text = NULL,
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888030305157';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = NULL,
    ingredients_text = NULL,
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888030305164';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = 'en:no-added-sugar,en:no-additives',
    labels_en = 'No added sugar,No additives'
WHERE barcode = '8888030305621';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = 'en:no-added-sugar,en:no-additives',
    labels_en = 'No added sugar,No additives'
WHERE barcode = '8888030305638';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Peanut Paste, Stabiliser (E471) and Peanut Oil.',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888030310694';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Peanut Paste, Stabiliser (E471) and Peanut Oil.',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888030314470';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Peanut Paste, Stabiliser (E471) and Peanut Oil.',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888030314487';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888192501572';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260012986';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260015956';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260016816';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260016823';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260016915';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888260020325';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:food',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = 'en:peanut-butter',
    labels_en = 'Peanut-butter'
WHERE barcode = '8888431104175';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888626127941';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888919320073';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888919320097';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '8888919320127';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:spreads,en:nuts-and-their-products,en:plant-based-spreads,en:oilseed-purees,en:nut-butters,en:mixed-nut-butters',
    main_category_en = 'Spreads',
    ingredients_text = NULL,
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = 'sesame seeds and peanuts',
    traces_en = 'sesame seeds and peanuts',
    labels_tags = 'en:health-star-rating,en:health-star-rating-5',
    labels_en = 'Health Star Rating,Health Star Rating 5'
WHERE barcode = '9310885100018';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '9310885115333';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:spreads,en:nuts-and-their-products,en:plant-based-spreads,en:oilseed-purees,en:nut-butters,en:mixed-nut-butters',
    main_category_en = 'Mixed nut butters',
    ingredients_text = 'Almonds, Cashews, Brazil Nuts, Natural Sea Salt',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = 'en:nuts,en:peanuts',
    traces_en = 'en:nuts,en:peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '9415748007234';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = NULL,
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '9551016530124';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = 'Spreads',
    ingredients_text = 'Salmon Fish, Soya Bean Oil, Waterchestnut, Vinegar, Sugar, Sweetcorn, Carrot, Modified Tapioca Starch, Egg Powder, Salt, Edible Gum',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '9556041613880';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = 'Spreads',
    ingredients_text = 'Wild caught tuna fish, Sugar, Fish sauce (fish extract, salt, sugar), Soya sauce (soya bean, salt, wheat), Modified starch, Soya bean oil, Five spice powder, Xanthan gum, Paprika extract',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = 'en:no-preservatives',
    labels_en = 'No preservatives'
WHERE barcode = '9556041614085';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = 'Spreads',
    ingredients_text = 'Wild Caught Tuna Fish, Soya Bean Oil, Fried Shallots (Shallots, Palm Oil, Rice Flour), Tomato Paste, Sugar, Chilli, Dried Shrimp, Galangal, Lemongrass, Tamarind, Modified Starch, Salt, Garlic, Xanthan Gum, Curry Leaves',
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = 'en:no-preservatives',
    labels_en = 'No preservatives'
WHERE barcode = '9556041614092';

UPDATE products
SET
    category_tags = 'en:spreads',
    main_category_en = NULL,
    ingredients_text = NULL,
    allergens = NULL,
    allergens_en = NULL,
    traces_tags = NULL,
    traces_en = NULL,
    labels_tags = NULL,
    labels_en = NULL
WHERE barcode = '9556231160026';

UPDATE products
SET
    category_tags = 'en:plant-based-foods-and-beverages,en:plant-based-foods,en:legumes-and-their-products,en:spreads,en:plant-based-spreads,en:oilseed-purees,en:legume-butters,en:peanut-butters,en:crunchy-peanut-butters',
    main_category_en = 'Peanut butters',
    ingredients_text = 'Roasted Peanuts, Sugar, Hydrogenated Vegetable Oil (Cottonseed, Soybean and Rapeseed Oil), Salt',
    allergens = 'en:peanuts',
    allergens_en = 'Peanuts',
    traces_tags = 'en:peanuts',
    traces_en = 'Peanuts',
    labels_tags = 'en:halal',
    labels_en = 'Halal'
WHERE barcode = '9922598800491';
