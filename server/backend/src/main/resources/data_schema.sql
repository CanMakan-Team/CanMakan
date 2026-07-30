-- Disable foreign key checks during initialization
SET FOREIGN_KEY_CHECKS = 0;

-- Drop all old tables if they exist
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
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_roles FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    theme VARCHAR(20) DEFAULT 'LIGHT',
    notifications_enabled TINYINT(1) DEFAULT 1,
    language VARCHAR(10) DEFAULT 'en',
    CONSTRAINT fk_user_preferences_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- DOMAIN 2: FAMILIES & DIETARY PROFILES
-- ============================================================================

CREATE TABLE families (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_name VARCHAR(100) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_families_users FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_members (
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(30) NOT NULL DEFAULT 'MEMBER', -- 'PRIMARY_ADMIN' or 'MEMBER'
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (family_id, user_id),
    CONSTRAINT fk_fam_members_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_fam_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    invited_email VARCHAR(255) NOT NULL,
    invitation_token VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'EXPIRED'
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fam_invites_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dietary_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    profile_name VARCHAR(100) NOT NULL,
    relationship VARCHAR(30) DEFAULT 'SELF',
    is_primary TINYINT(1) DEFAULT 0,
    avatar_url VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dietary_profiles_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dietary_restrictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE, -- e.g., 'HALAL', 'LACTOSE_INTOLERANT'
    display_name VARCHAR(155) NOT NULL,
    category VARCHAR(45) NOT NULL, -- 'ALLERGEN', 'RELIGIOUS', 'DIET'
    description TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Composite Primary Key Junction Table
CREATE TABLE profile_restrictions (
    dietary_profile_id BIGINT NOT NULL,
    dietary_restriction_id BIGINT NOT NULL,
    severity_level VARCHAR(20) DEFAULT 'STRICT_AVOID',
    PRIMARY KEY (dietary_profile_id, dietary_restriction_id),
    CONSTRAINT fk_prof_rest_profile FOREIGN KEY (dietary_profile_id) REFERENCES dietary_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_prof_rest_restriction FOREIGN KEY (dietary_restriction_id) REFERENCES dietary_restrictions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- DOMAIN 3: PRODUCTS & INGREDIENT ONTOLOGY
-- ============================================================================

-- Flattened Ingredient Taxonomy
CREATE TABLE ingredients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_name VARCHAR(255) NOT NULL UNIQUE,
    parent_allergen VARCHAR(100) NULL, -- e.g., 'Milk Derivatives'
    root_allergen VARCHAR(100) NULL,   -- e.g., 'DAIRY'
    is_chemical_alias TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consolidated Product & Nutrition Table
CREATE TABLE products (
    barcode VARCHAR(50) PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NULL,
    category VARCHAR(150) NULL,
    ingredients_text TEXT NULL,
    image_url TEXT NULL,
    countries_tags VARCHAR(255) NULL,
    raw_off_payload JSON NULL,
    -- Merged Nutrition Columns
    sugars_100g DECIMAL(6,2) NULL,
    sodium_100g DECIMAL(6,2) NULL,
    trans_fat_100g DECIMAL(6,2) NULL,
    saturated_fat_100g DECIMAL(6,2) NULL,
    fat_100g DECIMAL(6,2) NULL,
    energy_kcal_100g DECIMAL(6,2) NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Composite Primary Key Junction Table
CREATE TABLE product_ingredients (
    barcode VARCHAR(50) NOT NULL,
    ingredient_id BIGINT NOT NULL,
    PRIMARY KEY (barcode, ingredient_id),
    CONSTRAINT fk_prod_ing_product FOREIGN KEY (barcode) REFERENCES products(barcode) ON DELETE CASCADE,
    CONSTRAINT fk_prod_ing_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- DOMAIN 4: SCANNING, OCR & AI EXECUTION
-- ============================================================================

-- Unified Scans Table with JSON findings
CREATE TABLE scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    barcode VARCHAR(50) NULL,
    verdict VARCHAR(20) NOT NULL, -- 'SAFE', 'UNSAFE', 'WARNING'
    ai_explanation TEXT NULL,
    findings_json JSON NULL, -- Array: [{"ingredient": "Whey", "reason": "Triggers Dairy", "severity": "HIGH"}]
    scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_scans_profile FOREIGN KEY (profile_id) REFERENCES dietary_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_scans_product FOREIGN KEY (barcode) REFERENCES products(barcode) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ocr_scan_results (
    scan_id BIGINT PRIMARY KEY,
    raw_ocr_text TEXT NOT NULL,
    confidence_score DECIMAL(5,2) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ocr_scans FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    CONSTRAINT fk_ai_logs_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- DOMAIN 5: SUBSCRIPTIONS & USAGE
-- ============================================================================

CREATE TABLE subscription_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    price_cents INT DEFAULT 0,
    billing_period VARCHAR(20) DEFAULT 'MONTHLY'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL UNIQUE,
    plan_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Composite Primary Key Junction Table
CREATE TABLE plan_features (
    plan_id BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    limit_value INT DEFAULT -1, -- -1 represents Unlimited
    PRIMARY KEY (plan_id, feature_id),
    CONSTRAINT fk_pf_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_pf_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feature_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    current_usage INT DEFAULT 0,
    reset_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_fu_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_fu_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action_performed VARCHAR(255) NOT NULL, -- e.g., 'EXPORT_ANALYTICS_CSV', 'SUSPEND_USER'
    target_entity VARCHAR(50) NULL,
    details JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;