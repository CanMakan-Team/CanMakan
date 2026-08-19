-- UC5 Tan-family demo gold set (sources + wanted alts + forbidden near-misses).
-- Targeted overlays only; does not rewrite 01_products.sql INSERT rows.

-- ---------------------------------------------------------------------------
-- Profile 1 Sarah — wheat flour source
-- ---------------------------------------------------------------------------
UPDATE products
SET main_category_en = 'Wheat flours'
WHERE barcode = '4894514060287'
  AND (main_category_en IS NULL OR TRIM(main_category_en) = '' OR main_category_en <> 'Wheat flours');

-- Wanted GF flour
UPDATE products
SET
    main_category_en = COALESCE(NULLIF(TRIM(main_category_en), ''), 'Brown Rice Flour'),
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:gluten-free-flour,Gluten free flour,en:brown-rice-flour'
        ELSE TRIM(BOTH ',' FROM CONCAT(
            CASE
                WHEN CONCAT(',', category_tags, ',') LIKE '%,en:gluten-free-flour,%' THEN category_tags
                ELSE CONCAT(category_tags, ',', 'en:gluten-free-flour')
            END,
            CASE
                WHEN CONCAT(',', category_tags, ',') LIKE '%,en:brown-rice-flour,%' THEN ''
                ELSE ',en:brown-rice-flour'
            END
        ))
    END,
    labels_tags = CASE
        WHEN labels_tags IS NULL OR TRIM(labels_tags) = '' THEN 'en:no-gluten'
        WHEN CONCAT(',', labels_tags, ',') LIKE '%,en:no-gluten,%' THEN labels_tags
        ELSE CONCAT(labels_tags, ',', 'en:no-gluten')
    END
WHERE barcode = '8887501030642';

-- Profile 1 backup — Honey Stars source stays breakfast cereal
UPDATE products
SET main_category_en = 'Breakfast cereals'
WHERE barcode = '4800361385046';

UPDATE products
SET
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'Gluten free Breakfast cereals'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,Gluten free Breakfast cereals,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'Gluten free Breakfast cereals')
    END,
    labels_tags = CASE
        WHEN labels_tags IS NULL OR TRIM(labels_tags) = '' THEN 'en:no-gluten'
        WHEN CONCAT(',', labels_tags, ',') LIKE '%,en:no-gluten,%' THEN labels_tags
        ELSE CONCAT(labels_tags, ',', 'en:no-gluten')
    END
WHERE barcode = '9315090200706';

-- Forbid: oat cereals must not carry GF bread tag
UPDATE products
SET category_tags = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', IFNULL(category_tags, ''), ','), ',Gluten free bread,', ','))
WHERE barcode IN ('8887143802515', '8886478600698')
  AND CONCAT(',', IFNULL(category_tags, ''), ',') LIKE '%,Gluten free bread,%';

-- Forbid: almond-flour wraps are not baking-flour substitutes
UPDATE products
SET category_tags = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', IFNULL(category_tags, ''), ','), ',en:gluten-free-flour,', ','))
WHERE barcode = '8881300655204'
  AND CONCAT(',', IFNULL(category_tags, ''), ',') LIKE '%,en:gluten-free-flour,%';

-- ---------------------------------------------------------------------------
-- Profile 2 Michael — fish sauce + low-sodium sauces
-- ---------------------------------------------------------------------------
UPDATE products
SET main_category_en = 'Sauces'
WHERE barcode = '8850581172007'
  AND (main_category_en IS NULL OR main_category_en IN ('Groceries', ''));

UPDATE products
SET
    main_category_en = COALESCE(NULLIF(TRIM(main_category_en), ''), 'Sauces'),
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:sauces,Low sodium sauces'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,Low sodium sauces,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'Low sodium sauces')
    END,
    labels_tags = CASE
        WHEN labels_tags IS NULL OR TRIM(labels_tags) = '' THEN 'en:reduced-salt'
        WHEN CONCAT(',', labels_tags, ',') LIKE '%,en:reduced-salt,%' THEN labels_tags
        ELSE CONCAT(labels_tags, ',', 'en:reduced-salt')
    END
WHERE barcode IN ('0078895160482', '12456419');

-- ---------------------------------------------------------------------------
-- Profile 3 Emily — Farmhouse milk + unsweetened plant milks
-- ---------------------------------------------------------------------------
UPDATE products
SET main_category_en = 'Fresh milks'
WHERE barcode = '8888200602734'
  AND (main_category_en IS NULL OR main_category_en = 'Dairies');

UPDATE products
SET
    main_category_en = 'Fresh milks',
    ingredients_text = CASE
        WHEN ingredients_text IS NOT NULL AND TRIM(ingredients_text) = 'Fresh milks' THEN NULL
        ELSE ingredients_text
    END
WHERE barcode = '8888200602857';

UPDATE products
SET
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:milk-substitutes,en:dairy-substitutes'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,en:milk-substitutes,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'en:milk-substitutes')
    END,
    labels_tags = CASE
        WHEN labels_tags IS NULL OR TRIM(labels_tags) = '' THEN 'en:unsweetened,en:no-sugar'
        WHEN CONCAT(',', labels_tags, ',') LIKE '%,en:unsweetened,%' THEN labels_tags
        ELSE CONCAT(labels_tags, ',', 'en:unsweetened')
    END
WHERE barcode IN ('8850025000521', '8850025060105');

UPDATE products
SET
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:milk-substitutes,en:oat-based-drinks'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,en:milk-substitutes,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'en:milk-substitutes')
    END
WHERE barcode = '7394376618253';

-- Profile 3 backup — peanut butter + nut/seed butters
UPDATE products
SET
    main_category_en = 'Peanut butters',
    allergens = CASE
        WHEN allergens IS NULL OR TRIM(allergens) = '' THEN 'en:peanuts'
        ELSE allergens
    END
WHERE barcode = '8888260007616';

UPDATE products
SET
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:oilseed-purees,en:nut-butters'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,en:nut-butters,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'en:nut-butters')
    END
WHERE barcode = '95539553';

UPDATE products
SET
    category_tags = CASE
        WHEN category_tags IS NULL OR TRIM(category_tags) = '' THEN 'en:oilseed-purees,en:cereal-butters,en:tahini'
        WHEN CONCAT(',', category_tags, ',') LIKE '%,en:tahini,%' THEN category_tags
        ELSE CONCAT(category_tags, ',', 'en:tahini')
    END
WHERE barcode = '8888536703136';

-- Forbid Magnum coconut tub with declared milk
UPDATE products
SET allergens = CASE
    WHEN allergens IS NULL OR TRIM(allergens) = '' THEN 'en:milk'
    WHEN CONCAT(',', allergens, ',') LIKE '%,en:milk,%' THEN allergens
    ELSE CONCAT(allergens, ',', 'en:milk')
END
WHERE barcode = '0797776401192';
