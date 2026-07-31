-- =============================================
-- ROLES
-- =============================================
INSERT INTO roles (id, name) VALUES
(1, 'ADMIN'),
(2, 'USER');

-- =============================================
-- USERS (3 Admins + 10 App Users)
-- =============================================
INSERT INTO users (id, role_id, email, password_hash, is_active, created_at, updated_at) VALUES
-- 3 Admin Accounts (role_id = 1)
(1, 1, 'admin1@lovingcare.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(2, 1, 'admin2@lovingcare.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(3, 1, 'sysadmin@lovingcare.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),

-- 10 App User Accounts (role_id = 2)
(4, 2, 'sarah@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(5, 2, 'michael@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(6, 2, 'emily@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(7, 2, 'david@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(8, 2, 'jessica@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(9, 2, 'daniel@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(10, 2, 'amanda@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(11, 2, 'james@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(12, 2, 'olivia@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW()),
(13, 2, 'robert@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW', 1, NOW(), NOW());