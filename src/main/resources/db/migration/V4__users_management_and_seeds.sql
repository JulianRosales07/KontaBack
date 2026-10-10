-- V4__users_management_and_seeds.sql
-- User management enhancements and seed users for Konta ERP

-- 1. Add extra columns to users table if they do not exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP WITH TIME ZONE;

-- 2. Update existing admin user with full name and updated email
UPDATE users 
SET full_name = 'Administrador Principal', 
    email = 'admin@konta.com' 
WHERE username = 'admin';

-- 3. Seed Contador User: contador / contador123
INSERT INTO users (id, username, email, password, full_name, phone, role, enabled, created_at, updated_at, created_by, updated_by)
SELECT 
    'a0000000-0000-0000-0000-000000000002',
    'contador',
    'contador@konta.com',
    '$2a$10$slYQmyNdGzTn7ZLBXBChFOC9f6kFjAqPhccnP6DxlWXx2lPk1C3G6',
    'Carlos Ruiz - Contador Titular',
    '+57 310 123 4567',
    'ROLE_CONTADOR',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'contador');

-- 4. Seed Auxiliar Contable: auxiliar / auxiliar123
INSERT INTO users (id, username, email, password, full_name, phone, role, enabled, created_at, updated_at, created_by, updated_by)
SELECT 
    'a0000000-0000-0000-0000-000000000003',
    'auxiliar',
    'auxiliar@konta.com',
    '$2a$10$slYQmyNdGzTn7ZLBXBChFOC9f6kFjAqPhccnP6DxlWXx2lPk1C3G6',
    'Laura Gómez - Auxiliar Contable',
    '+57 320 987 6543',
    'ROLE_AUXILIAR',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'auxiliar');
