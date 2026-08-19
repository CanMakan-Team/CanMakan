# CanMakan Dietary Rule Specification

**Status: Implemented** in [`DietaryRuleEngine`](../../server/backend/src/main/java/com/canmakan/backend/product/verdict/DietaryRuleEngine.java) and SQL seed for the MVP codes below. Live severities are `STRICT_AVOID` and `INTOLERANCE` only. `PREFERENCE`, OCR intake, and MUIS certification claims are out of the live engine.

## 1. Purpose

This document defines deterministic MVP rules for the CanMakan dietary rule engine. The rules are intended to support:

- backend implementation;
- database seed preparation;
- checker unit tests;
- `DietaryRuleEngine` integration tests; and
- consistent frontend explanations.

This specification covers:

- `HALAL`;
- `VEGETARIAN`;
- `VEGAN`;
- `LOW_SUGAR`;
- `LOW_FAT`;
- `LOW_TRANS_FAT`; and
- `LOW_SODIUM`.

`KOSHER`, `KETO`, `LOW_CARB`, personalised medical limits, serving-based nutrition limits, and product recommendations are outside this MVP rule scope.

## 2. Important limitations

1. CanMakan provides dietary screening support and does not replace medical, religious, regulatory, or professional advice.
2. A HALAL result is an informational product-data assessment. CanMakan must not claim that a product is MUIS-certified unless certification data comes from an authoritative certification source.
3. Open Food Facts labels and ingredients may be incomplete, user-contributed, outdated, or inaccurate.
4. Missing information must never be treated as confirmed `SAFE`.
5. A numeric zero must be distinguished from missing nutrition data.
6. Only standardised ingredients and approved ingredient-restriction mappings may produce deterministic ingredient conflicts.
7. Raw `ingredients_text` keyword matching must not be the primary production rule.

## 3. Shared restriction codes

The proposed MVP restriction catalog is:

| Code | Category | Rule type |
|------|----------|-----------|
| `HALAL` | `RELIGIOUS` | Certification and ingredient rule |
| `VEGETARIAN` | `DIET` | Ingredient-based dietary preference |
| `VEGAN` | `DIET` | Ingredient-based dietary preference |
| `LOW_SUGAR` | `DIET` | Numeric nutrition threshold |
| `LOW_FAT` | `DIET` | Numeric nutrition threshold |
| `LOW_TRANS_FAT` | `DIET` | Numeric nutrition threshold |
| `LOW_SODIUM` | `DIET` | Numeric nutrition threshold |

`HALAL` and `VEGETARIAN` already exist in the current backend seed data. `VEGAN` and the four nutrition codes are proposed additions. All proposed codes must be approved before SQL seed changes are made.

## 4. Shared verdict semantics

The backend verdict values for this specification are:

- `SAFE`;
- `WARNING`; and
- `UNSAFE`.

`AVOID` must not be used as a backend enum value.

### SAFE

Use `SAFE` only when:

- all data required for the selected rule is present;
- no applicable conflict is found; and
- any required positive evidence is present.

### WARNING

Use `WARNING` when:

- required data is missing or incomplete;
- certification cannot be verified;
- a nutrition value is unavailable;
- a rule cannot reach a deterministic result; or
- the user profile severity requires a warning rather than strict avoidance.

A `WARNING` must include an explanatory `Finding`.

### UNSAFE

Use `UNSAFE` when:

- an approved deterministic rule is violated; and
- the corresponding profile restriction severity is `STRICT_AVOID`.

Checker implementations append `Finding` objects. `DietaryRuleEngine` performs final severity aggregation across the active restrictions.

Java currently uses `AVOID` internally while the database uses `UNSAFE`. The implementation must be aligned in a later code change.

## 5. Missing-data policy

| Data state | Required behaviour |
|------------|--------------------|
| Required field is null | `WARNING` |
| Product-data field is absent | `WARNING` |
| Ingredient list is incomplete | `WARNING` for ingredient-based rules |
| Nutrition object is null | `WARNING` for nutrition rules |
| Nutrition field is null | `WARNING` for that specific nutrition rule |
| Confirmed numeric zero | Treat as a real numeric value |
| Zero may represent missing OFF data | `WARNING` until absence can be distinguished |
| Unknown ingredient origin | `WARNING` when origin affects the selected rule |

`null` means unavailable. `BigDecimal.ZERO` means a confirmed numeric zero. Open Food Facts mapping code must preserve missing values as `null`.

## 6. HALAL rule

- Restriction code: `HALAL`
- Category: `RELIGIOUS`
- Primary evidence:
  - canonical Halal label tags; and
  - approved ingredient-to-HALAL conflict mappings.
- `KOSHER` is outside scope.

### 6.1 Canonical label processing

The recognised MVP input tags are:

- `en:halal`
- `halal`

Normalise the current `labels_tags` value as follows:

1. Split the value using commas.
2. Trim leading and trailing whitespace from each entry.
3. Convert each entry to lowercase using `Locale.ROOT`.
4. Ignore empty entries.
5. Match exact normalised tags only.

The presence of a recognised tag means: “Halal-labelled in the available product data.” It must not automatically be described as “MUIS-certified.”

### 6.2 HALAL ingredient conflicts

HALAL ingredient conflicts must come from the proposed `ingredient_restrictions` mapping. The initial mapping dataset must be based on approved standardised ingredients, not raw-text searches.

The minimum proposed mapping concepts are:

- pork;
- swine;
- lard;
- pork gelatine;
- alcoholic beverage; and
- intoxicating alcohol used as a product ingredient.

Do not treat every occurrence of a chemical alcohol term as an intoxicating alcohol rule.

Ambiguous ingredients, animal-derived additives, gelatine with unknown source, and ingredients requiring slaughter-method verification must be treated as uncertain unless an approved mapping resolves them.

### 6.3 HALAL decision table

| Halal tag | Approved conflicting ingredient | Required result |
|-----------|---------------------------------|-----------------|
| Present | No | `SAFE` |
| Present | Yes | `UNSAFE` |
| Absent | Yes | `UNSAFE` |
| Absent | No | `WARNING`: Halal status cannot be verified |
| Unknown/incomplete labels | No conflict | `WARNING` |
| Incomplete ingredients | No confirmed conflict | `WARNING` |

A confirmed approved ingredient conflict takes precedence over a Halal product tag.

### 6.4 HALAL findings

Confirmed conflict:

- `restrictionCode`: `HALAL`
- `ingredientName`: standardised ingredient name
- `reason`: “<ingredient> conflicts with the HALAL restriction.”

Missing certification evidence:

- `restrictionCode`: `HALAL`
- `ingredientName`: `null`
- `reason`: “Halal certification information could not be verified from the available product data.”

The current `Finding` and engine contracts may need adjustment so that an uncertainty `Finding` does not become `UNSAFE` merely because the profile uses `STRICT_AVOID`.

## 7. VEGETARIAN rule

The MVP dietary definition is lacto-ovo vegetarian. This is a CanMakan project operational definition.

Allowed:

- plant ingredients;
- dairy;
- eggs; and
- honey.

Excluded:

- meat;
- poultry;
- fish;
- seafood;
- animal body parts;
- lard;
- animal-derived gelatine; and
- animal rennet when explicitly identified as animal-derived.

### 7.1 VEGETARIAN processing

Use only:

- standardised ingredients; and
- approved `ingredient_restrictions` mappings.

A vegetarian product label is not required. A vegetarian label may be supporting evidence, but it must not override an approved conflicting ingredient mapping.

### 7.2 VEGETARIAN decision table

| Ingredient data | Confirmed conflict | Required result |
|-----------------|--------------------|-----------------|
| Complete | No | `SAFE` |
| Complete | Yes | `UNSAFE` |
| Incomplete | No confirmed conflict | `WARNING` |
| Missing | Unknown | `WARNING` |

Finding example:

- `restrictionCode`: `VEGETARIAN`
- `ingredientName`: standardised ingredient name
- `reason`: “<ingredient> conflicts with the VEGETARIAN restriction.”

## 8. VEGAN rule

- Restriction code: `VEGAN`
- Category: `DIET`
- `VEGAN` must be added to the backend restriction catalog before implementation.

The MVP rule excludes ingredients derived wholly or partly from animals.

Initial proposed conflict concepts include:

- meat;
- poultry;
- fish;
- seafood;
- shellfish;
- insects;
- dairy;
- milk;
- whey;
- casein;
- egg;
- honey;
- animal-derived gelatine;
- lard;
- animal-derived rennet;
- carmine; and
- shellac.

These concepts must become approved standardised ingredient mappings before production use.

### 8.1 VEGAN processing

Use standardised ingredient mappings only. A vegan label may be supporting evidence, but it must not override a confirmed conflicting ingredient.

### 8.2 VEGAN decision table

| Ingredient data | Confirmed conflict | Required result |
|-----------------|--------------------|-----------------|
| Complete | No | `SAFE` |
| Complete | Yes | `UNSAFE` |
| Incomplete | No confirmed conflict | `WARNING` |
| Missing | Unknown | `WARNING` |

Finding example:

- `restrictionCode`: `VEGAN`
- `ingredientName`: standardised ingredient name
- `reason`: “<ingredient> conflicts with the VEGAN restriction.”

## 9. Nutrition rule basis

MVP nutrition checks use values per 100 g.

| Restriction | Java field | Database field |
|-------------|------------|----------------|
| `LOW_SUGAR` | `sugarsPer100g` | `sugars_100g` |
| `LOW_FAT` | `fatPer100g` | `fat_100g` |
| `LOW_TRANS_FAT` | `transFatPer100g` | `trans_fat_100g` |
| `LOW_SODIUM` | `sodiumPer100g` | `sodium_100g` |

Sugar, total fat, and trans fat use grams per 100 g. Sodium is stored as grams per 100 g. UI explanations may additionally show sodium in milligrams. `0.12 g` sodium equals `120 mg` sodium.

The current MVP thresholds apply to solid food values per 100 g. Products requiring drink-specific per-100 ml rules must return `WARNING` until the system can reliably distinguish food from beverages and apply a separately approved beverage specification.

## 10. LOW_SUGAR rule

- Restriction code: `LOW_SUGAR`
- Category: `DIET`
- Field: `sugarsPer100g`
- Unit: g per 100 g
- Maximum qualifying value: `5.0 g` per 100 g
- Comparison:
  - value `<= 5.0`: no conflict;
  - value `> 5.0`: conflict.
- Equality at `5.0` is allowed.

| `sugarsPer100g` | Result |
|-----------------|--------|
| `null` | `WARNING` |
| Confirmed `0` | `SAFE` |
| `<= 5.0` | `SAFE` |
| `> 5.0` | Add a `Finding`; final result is based on profile severity |

Finding reason format:

> Sugar is <observed> g per 100 g, above the LOW_SUGAR limit of 5.0 g per 100 g.

## 11. LOW_FAT rule

- Restriction code: `LOW_FAT`
- Category: `DIET`
- Field: `fatPer100g`
- Unit: g per 100 g
- Maximum qualifying value: `3.0 g` per 100 g
- Comparison:
  - value `<= 3.0`: no conflict;
  - value `> 3.0`: conflict.
- Equality at `3.0` is allowed.

| `fatPer100g` | Result |
|--------------|--------|
| `null` | `WARNING` |
| Confirmed `0` | `SAFE` |
| `<= 3.0` | `SAFE` |
| `> 3.0` | Add a `Finding`; final result is based on profile severity |

Finding reason format:

> Total fat is <observed> g per 100 g, above the LOW_FAT limit of 3.0 g per 100 g.

`LOW_FAT` uses total fat, not saturated fat.

## 12. LOW_TRANS_FAT rule

- Restriction code: `LOW_TRANS_FAT`
- Category: `DIET`
- Field: `transFatPer100g`
- Unit: g per 100 g
- MVP qualifying value: exactly `0 g` per 100 g
- Comparison:
  - confirmed value `== 0`: no conflict;
  - confirmed value `> 0`: conflict.
- Negative values are invalid data and must produce `WARNING`.

This is a conservative CanMakan MVP operational rule. It is not presented as a universal statutory “low trans fat” threshold.

| `transFatPer100g` | Result |
|-------------------|--------|
| `null` | `WARNING` |
| Negative | `WARNING`: invalid data |
| Confirmed `0` | `SAFE` |
| `> 0` | Add a `Finding`; final result is based on profile severity |

Finding reason format:

> Trans fat is <observed> g per 100 g; the LOW_TRANS_FAT rule requires a confirmed value of 0 g per 100 g.

## 13. LOW_SODIUM rule

- Restriction code: `LOW_SODIUM`
- Category: `DIET`
- Field: `sodiumPer100g`
- Stored unit: g per 100 g
- Display unit: mg per 100 g
- Maximum qualifying value:
  - `0.12 g` per 100 g;
  - equivalent to `120 mg` per 100 g.
- Comparison:
  - value `<= 0.12`: no conflict;
  - value `> 0.12`: conflict.
- Equality at `0.12` is allowed.

| `sodiumPer100g` | Result |
|-----------------|--------|
| `null` | `WARNING` |
| Confirmed `0` | `SAFE` |
| `<= 0.12 g` | `SAFE` |
| `> 0.12 g` | Add a `Finding`; final result is based on profile severity |

Finding reason format:

> Sodium is <observedMg> mg per 100 g, above the LOW_SODIUM limit of 120 mg per 100 g.

`LOW_SALT` must not be used as a backend synonym unless the team explicitly approves an alias. The canonical backend code for this specification is `LOW_SODIUM`.

## 14. Numeric validation rules

1. Use `BigDecimal.compareTo()`, not double comparison.
2. Do not compare `BigDecimal` values using `equals()`.
3. Negative nutrition values are invalid and produce `WARNING`.
4. `null` produces `WARNING`.
5. Confirmed zero is valid.
6. Missing Open Food Facts fields must map to `null`.
7. Do not infer missing values from zero inside `NutritionChecker`.
8. Threshold constants must later be centralised in an approved rule source, not duplicated across tests and production code.
9. Finding reasons must include:
   - observed value;
   - unit;
   - approved threshold; and
   - restriction code.

## 15. Required ingredient restriction model

The implementation requires this many-to-many relationship:

```text
ingredient ↔ dietary restriction
```

Proposed table: `ingredient_restrictions`

Minimum columns:

- `ingredient_id`
- `dietary_restriction_id`

Minimum key:

- composite primary key on `ingredient_id` and `dietary_restriction_id`.

One standardised ingredient may conflict with multiple restrictions. For example:

- animal-derived gelatine may conflict with `HALAL`, `VEGETARIAN`, and `VEGAN`, depending on its approved source mapping; and
- dairy may conflict with `VEGAN` but not the MVP lacto-ovo `VEGETARIAN` rule.

Examples in this specification do not automatically become production seed data. They require team review before insertion.

## 16. Checker architecture

The intended checker classes are:

- `ReligiousChecker`
  - handles `HALAL` only;
- `DietaryPreferenceChecker`
  - handles `VEGETARIAN` and `VEGAN`;
- `NutritionChecker`
  - handles `LOW_SUGAR`;
  - handles `LOW_FAT`;
  - handles `LOW_TRANS_FAT`; and
  - handles `LOW_SODIUM`.

All three classes implement `RestrictionChecker`.

Because `RestrictionChecker.supports()` currently checks only the category, checkers sharing `DIET` must also filter supported restriction codes inside `check()`.

- `ReligiousChecker` supports `RELIGIOUS` and processes only `HALAL`.
- `DietaryPreferenceChecker` supports `DIET` and processes only `VEGETARIAN` or `VEGAN`.
- `NutritionChecker` supports `DIET` and processes only the four approved nutrition codes.
- Unsupported codes must be ignored without adding a `Finding`.

## 17. Data dependencies

### HALAL

- Canonical label parser
- `labelTags` populated in `ProductData`
- `ingredient_restrictions` table
- Approved HALAL ingredient mappings
- Uncertainty handling in `Finding` and `DietaryRuleEngine`

### VEGETARIAN and VEGAN

- `VEGAN` restriction catalog entry
- `ingredient_restrictions` table
- Approved vegetarian and vegan mappings
- Standardised product ingredients
- Complete `ProductData` assembly

### Nutrition

- Four backend restriction catalog entries
- Open Food Facts nutrition DTO and mapper
- Product persistence mapping
- `ProductDataAssembler`
- Missing values preserved as `null`
- Audit of seed values currently stored as zero
- Nutrition fields populated in `ProductData`

## 18. Test acceptance criteria

### HALAL

1. Recognised Halal tag and no conflict
2. No Halal tag and no conflict
3. Explicit conflicting ingredient
4. Tag plus explicit conflict
5. Incomplete ingredient data
6. Null labels
7. Tag normalisation
8. Unsupported religious code

### VEGETARIAN

1. No mapped conflict
2. Mapped meat conflict
3. Dairy allowed
4. Egg allowed
5. Incomplete ingredients
6. Unsupported `DIET` code

### VEGAN

1. No mapped conflict
2. Meat conflict
3. Dairy conflict
4. Egg conflict
5. Honey conflict
6. Animal-derived additive conflict
7. Incomplete ingredients

### Nutrition

For each of the four nutrition rules:

1. Null value
2. Confirmed zero
3. Below threshold
4. Exactly equal to threshold
5. Above threshold
6. Negative value
7. Unsupported `DIET` code
8. Existing `Finding` list is preserved

## 19. Source basis

- [Majlis Ugama Islam Singapura, “For consumers — Basic Halal principles”](https://www.muis.gov.sg/halal/for-consumers/)
- [Majlis Ugama Islam Singapura, “Halal”](https://www.muis.gov.sg/halal/)
- [The Vegetarian Society, vegetarian definition and certification criteria](https://vegsoc.org/trademarks/vegetarian-certification/)
- [The Vegan Society, “Definition of veganism”](https://www.vegansociety.com/go-vegan/definition-veganism)
- [UK Government, front-of-pack nutrition labelling guidance](https://assets.publishing.service.gov.uk/media/69b3f02a9d8b52961a62b3c7/fop-guidance_0.pdf)
- [Singapore Ministry of Health, ban on partially hydrogenated oils](https://www.moh.gov.sg/newsroom/ban-on-partially-hydrogenated-oil-in-all-fats-oils-and-pre-packaged-foods-sold-in-singapore/)

External sources provide the reference basis. The exact CanMakan rules remain project operational rules, and the team must approve them before implementation. Future regulatory or product-scope changes require a versioned update to this specification.

## 20. Approval checklist

- [ ] HALAL tag rules approved
- [ ] HALAL missing-certification behaviour approved
- [ ] HALAL prohibited ingredient mappings approved
- [ ] VEGETARIAN definition approved
- [ ] VEGAN definition approved
- [ ] LOW_SUGAR code and threshold approved
- [ ] LOW_FAT code and threshold approved
- [ ] LOW_TRANS_FAT project rule approved
- [ ] LOW_SODIUM code and threshold approved
- [ ] SAFE / WARNING / UNSAFE vocabulary approved
- [ ] Missing-data policy approved
- [ ] Finding wording approved
- [ ] Database changes assigned
- [ ] Knowledge-base mappings assigned
- [ ] ProductData integration assigned
- [ ] Checker implementation assigned
- [ ] Unit and integration tests assigned
