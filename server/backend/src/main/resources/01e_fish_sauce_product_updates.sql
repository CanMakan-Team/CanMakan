-- Fix fish sauce row when an older duplicate INSERT left main_category_en = Groceries.
UPDATE products
SET
    main_category_en = 'Sauces',
    category_tags = 'en:condiments,en:sauces,en:nuoc-mam-sauce,en:groceries'
WHERE barcode = '8850581172007'
  AND main_category_en = 'Groceries';
