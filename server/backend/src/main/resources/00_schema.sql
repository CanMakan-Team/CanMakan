-- =============================================================================
-- CanMakan schema
-- Re-runnable initialization script
-- =============================================================================

-- CREATE DATABASE IF NOT EXISTS canmakan
--     CHARACTER SET utf8mb4
--     COLLATE utf8mb4_unicode_ci;

-- USE canmakan;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop all tables if they exist (child → parent)
DROP TABLE IF EXISTS admin_audit_logs;
DROP TABLE IF EXISTS daily_consumer_trends;
DROP TABLE IF EXISTS feature_usage;
DROP TABLE IF EXISTS plan_features;
DROP TABLE IF EXISTS features;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS subscription_plans;
DROP TABLE IF EXISTS ai_execution_logs;
DROP TABLE IF EXISTS ocr_scan_results;
DROP TABLE IF EXISTS scans;
DROP TABLE IF EXISTS product_ingredients;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS ingredients;
DROP TABLE IF EXISTS profile_restrictions;
DROP TABLE IF EXISTS dietary_restrictions;
DROP TABLE IF EXISTS dietary_profiles;
DROP TABLE IF EXISTS family_invitations;
DROP TABLE IF EXISTS family_members;
DROP TABLE IF EXISTS families;
DROP TABLE IF EXISTS user_preferences;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- DOMAIN 1: AUTHENTICATION & USERS
-- ============================================================================

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_roles
        FOREIGN KEY (role_id) REFERENCES roles(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_users
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    theme VARCHAR(20) DEFAULT 'LIGHT',
    notifications_enabled TINYINT(1) DEFAULT 1,
    `language` VARCHAR(10) DEFAULT 'en',
    CONSTRAINT fk_user_preferences_users
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- DOMAIN 2: FAMILIES & DIETARY PROFILES
-- ============================================================================

CREATE TABLE families (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_name VARCHAR(100) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_families_users
        FOREIGN KEY (created_by_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE family_members (
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(30) NOT NULL DEFAULT 'MEMBER', -- 'PRIMARY_ADMIN' or 'MEMBER'
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (family_id, user_id),
    CONSTRAINT fk_fam_members_family
        FOREIGN KEY (family_id) REFERENCES families(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_fam_members_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE family_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    invited_email VARCHAR(255) NOT NULL,
    invitation_token VARCHAR(100) NOT NULL UNIQUE,
    `status` VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'EXPIRED'
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fam_invites_family
        FOREIGN KEY (family_id) REFERENCES families(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dietary_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    profile_name VARCHAR(100) NOT NULL,
    relationship VARCHAR(30) DEFAULT 'SELF',
    is_primary TINYINT(1) DEFAULT 0,
    avatar_url VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dietary_profiles_family
        FOREIGN KEY (family_id) REFERENCES families(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dietary_restrictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) NOT NULL UNIQUE, -- e.g., 'HALAL', 'LACTOSE_INTOLERANT'
    display_name VARCHAR(155) NOT NULL,
    category VARCHAR(45) NOT NULL, -- 'ALLERGEN', 'RELIGIOUS', 'DIET'
    `description` TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profile_restrictions (
    dietary_profile_id BIGINT NOT NULL,
    dietary_restriction_id BIGINT NOT NULL,
    severity_level VARCHAR(20) DEFAULT 'STRICT_AVOID',
    PRIMARY KEY (dietary_profile_id, dietary_restriction_id),
    CONSTRAINT fk_prof_rest_profile
        FOREIGN KEY (dietary_profile_id) REFERENCES dietary_profiles(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_prof_rest_restriction
        FOREIGN KEY (dietary_restriction_id) REFERENCES dietary_restrictions(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- DOMAIN 3: PRODUCTS & INGREDIENT ONTOLOGY
-- ============================================================================

CREATE TABLE ingredients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_name VARCHAR(255) NOT NULL UNIQUE,
    parent_allergen VARCHAR(100) NULL, -- e.g., 'Milk Derivatives'
    root_allergen VARCHAR(100) NULL,   -- e.g., 'DAIRY'
    is_chemical_alias TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    barcode VARCHAR(50) PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NULL,
    category VARCHAR(255) NULL,
    ingredients_text TEXT NULL,
    image_url TEXT NULL,
    countries_tags VARCHAR(255) NULL,
    labels_tags VARCHAR(255) NULL,
    labels_en VARCHAR(255) NULL,
    sugars_100g DECIMAL(6,2) NULL,
    sodium_100g DECIMAL(6,2) NULL,
    trans_fat_100g DECIMAL(6,2) NULL,
    saturated_fat_100g DECIMAL(6,2) NULL,
    fat_100g DECIMAL(6,2) NULL,
    energy_kcal_100g DECIMAL(6,2) NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_ingredients (
    barcode VARCHAR(50) NOT NULL,
    ingredient_id BIGINT NOT NULL,
    PRIMARY KEY (barcode, ingredient_id),
    CONSTRAINT fk_prod_ing_product
        FOREIGN KEY (barcode) REFERENCES products(barcode)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_prod_ing_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- DOMAIN 4: SCANNING, OCR & AI EXECUTION
-- ============================================================================

CREATE TABLE scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    barcode VARCHAR(50) NULL, -- nullable: OCR-only / product not found
    verdict VARCHAR(20) NOT NULL, -- 'SAFE', 'UNSAFE', 'WARNING'
    ai_explanation TEXT NULL,
    findings_json JSON NULL, -- Array: [{"ingredient":"Whey","reason":"Triggers Dairy","severity":"HIGH"}]
    scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scans_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_scans_profile
        FOREIGN KEY (profile_id) REFERENCES dietary_profiles(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_scans_product
        FOREIGN KEY (barcode) REFERENCES products(barcode)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ocr_scan_results (
    scan_id BIGINT PRIMARY KEY,
    raw_ocr_text TEXT NOT NULL,
    confidence_score DECIMAL(5,2) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ocr_scans
        FOREIGN KEY (scan_id) REFERENCES scans(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_execution_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scan_id BIGINT NOT NULL,
    execution_tier VARCHAR(30) NOT NULL, -- 'TIER_1_RULES', 'TIER_3_LLM'
    model_id VARCHAR(50) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    latency_ms INT NULL,
    compiled_prompt JSON NULL,
    raw_llm_response JSON NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_logs_scan
        FOREIGN KEY (scan_id) REFERENCES scans(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- DOMAIN 5: SUBSCRIPTIONS & USAGE
-- ============================================================================

CREATE TABLE subscription_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL UNIQUE,
    `name` VARCHAR(100) NOT NULL,
    price_cents INT DEFAULT 0,
    billing_period VARCHAR(20) DEFAULT 'MONTHLY'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL UNIQUE,
    plan_id BIGINT NOT NULL,
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_family
        FOREIGN KEY (family_id) REFERENCES families(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_sub_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_code VARCHAR(50) NOT NULL UNIQUE,
    `description` TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE plan_features (
    plan_id BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    limit_value INT DEFAULT -1, -- -1 represents Unlimited
    PRIMARY KEY (plan_id, feature_id),
    CONSTRAINT fk_pf_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_pf_feature
        FOREIGN KEY (feature_id) REFERENCES features(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feature_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    current_usage INT DEFAULT 0,
    reset_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_fu_family
        FOREIGN KEY (family_id) REFERENCES families(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_fu_feature
        FOREIGN KEY (feature_id) REFERENCES features(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- DOMAIN 6: AUDIT & ANALYTICS
-- ============================================================================

CREATE TABLE daily_consumer_trends (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trend_date DATE NOT NULL UNIQUE,
    top_flagged_ingredient VARCHAR(255) NULL,
    total_scans_count INT DEFAULT 0,
    unsafe_scans_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action_performed VARCHAR(255) NOT NULL, -- e.g., 'EXPORT_ANALYTICS_CSV', 'SUSPEND_USER'
    target_entity VARCHAR(50) NULL,
    details JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- BATCH INDEXES
-- ============================================================================

CREATE INDEX idx_users_role_id ON users (role_id);
CREATE INDEX idx_users_is_active ON users (is_active);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens (expiry_date);

CREATE INDEX idx_families_created_by_user_id ON families (created_by_user_id);

CREATE INDEX idx_family_members_user_id ON family_members (user_id);

CREATE INDEX idx_family_invitations_family_id ON family_invitations (family_id);
CREATE INDEX idx_family_invitations_invited_email ON family_invitations (invited_email);
CREATE INDEX idx_family_invitations_status ON family_invitations (status);

CREATE INDEX idx_dietary_profiles_family_id ON dietary_profiles (family_id);

CREATE INDEX idx_dietary_restrictions_category ON dietary_restrictions (category);

CREATE INDEX idx_profile_restrictions_restriction_id
    ON profile_restrictions (dietary_restriction_id);

CREATE INDEX idx_ingredients_root_allergen ON ingredients (root_allergen);

CREATE INDEX idx_products_brand ON products (brand);
CREATE INDEX idx_products_category ON products (category);

CREATE INDEX idx_product_ingredients_ingredient_id
    ON product_ingredients (ingredient_id);

CREATE INDEX idx_scans_user_id ON scans (user_id);
CREATE INDEX idx_scans_profile_id ON scans (profile_id);
CREATE INDEX idx_scans_barcode ON scans (barcode);
CREATE INDEX idx_scans_scanned_at ON scans (scanned_at);
CREATE INDEX idx_scans_verdict ON scans (verdict);
CREATE INDEX idx_scans_profile_scanned_at ON scans (profile_id, scanned_at);

CREATE INDEX idx_ai_execution_logs_scan_id ON ai_execution_logs (scan_id);
CREATE INDEX idx_ai_execution_logs_execution_tier ON ai_execution_logs (execution_tier);

CREATE INDEX idx_subscriptions_plan_id ON subscriptions (plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions (status);

CREATE INDEX idx_plan_features_feature_id ON plan_features (feature_id);

CREATE INDEX idx_feature_usage_family_id ON feature_usage (family_id);
CREATE INDEX idx_feature_usage_feature_id ON feature_usage (feature_id);
CREATE INDEX idx_feature_usage_family_feature ON feature_usage (family_id, feature_id);

CREATE INDEX idx_admin_audit_logs_admin_user_id ON admin_audit_logs (admin_user_id);
CREATE INDEX idx_admin_audit_logs_created_at ON admin_audit_logs (created_at);