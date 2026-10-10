-- ==============================================================================
-- V8__rbac_roles_permissions.sql
-- Modelo de seguridad: USER -> USER_ROLE -> ROLE -> ROLE_PERMISSION -> PERMISSION
-- ==============================================================================

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_perm ON role_permissions(permission_id);

-- La columna legada users.role deja de usarse para autorización
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;

-- Roles base
INSERT INTO roles (id, code, name, description, is_system, created_by) VALUES
    (CAST('b0000000-0000-0000-0000-000000000001' AS UUID), 'SUPER_ADMIN', 'Super Administrador', 'Acceso total, incluida la gestión de usuarios, roles y permisos', TRUE, 'SYSTEM'),
    (CAST('b0000000-0000-0000-0000-000000000002' AS UUID), 'ADMINISTRADOR', 'Administrador', 'Acceso a todos los módulos funcionales excepto gestión de accesos', TRUE, 'SYSTEM');

-- Permisos granulares
INSERT INTO permissions (id, code, name, module) VALUES
    (CAST('b1000000-0000-0000-0000-000000000001' AS UUID), 'USERS_VIEW', 'Ver usuarios', 'USERS'),
    (CAST('b1000000-0000-0000-0000-000000000002' AS UUID), 'USERS_CREATE', 'Crear usuarios', 'USERS'),
    (CAST('b1000000-0000-0000-0000-000000000003' AS UUID), 'USERS_UPDATE', 'Editar usuarios y asignar roles', 'USERS'),
    (CAST('b1000000-0000-0000-0000-000000000004' AS UUID), 'USERS_DISABLE', 'Activar / desactivar usuarios', 'USERS'),
    (CAST('b1000000-0000-0000-0000-000000000005' AS UUID), 'USERS_RESET_PASSWORD', 'Restablecer contraseñas', 'USERS'),
    (CAST('b1000000-0000-0000-0000-000000000006' AS UUID), 'ROLES_VIEW', 'Ver roles', 'ROLES'),
    (CAST('b1000000-0000-0000-0000-000000000007' AS UUID), 'ROLES_MANAGE', 'Crear roles y asignar permisos', 'ROLES'),
    (CAST('b1000000-0000-0000-0000-000000000008' AS UUID), 'PERMISSIONS_VIEW', 'Ver catálogo de permisos', 'PERMISSIONS'),
    (CAST('b1000000-0000-0000-0000-000000000010' AS UUID), 'DASHBOARD_VIEW', 'Ver dashboard', 'DASHBOARD'),
    (CAST('b1000000-0000-0000-0000-000000000011' AS UUID), 'ASSOCIATES_VIEW', 'Ver asociados', 'ASSOCIATES'),
    (CAST('b1000000-0000-0000-0000-000000000012' AS UUID), 'ASSOCIATES_CREATE', 'Crear asociados', 'ASSOCIATES'),
    (CAST('b1000000-0000-0000-0000-000000000013' AS UUID), 'ASSOCIATES_UPDATE', 'Editar asociados', 'ASSOCIATES'),
    (CAST('b1000000-0000-0000-0000-000000000014' AS UUID), 'LOANS_VIEW', 'Ver préstamos', 'LOANS'),
    (CAST('b1000000-0000-0000-0000-000000000015' AS UUID), 'LOANS_CREATE', 'Crear / desembolsar préstamos', 'LOANS'),
    (CAST('b1000000-0000-0000-0000-000000000016' AS UUID), 'LOANS_UPDATE', 'Modificar / anular préstamos', 'LOANS'),
    (CAST('b1000000-0000-0000-0000-000000000017' AS UUID), 'COLLECTIONS_VIEW', 'Ver recaudos y abonos', 'COLLECTIONS'),
    (CAST('b1000000-0000-0000-0000-000000000018' AS UUID), 'COLLECTIONS_CREATE', 'Registrar recaudos y abonos', 'COLLECTIONS'),
    (CAST('b1000000-0000-0000-0000-000000000019' AS UUID), 'RECEIVABLES_VIEW', 'Ver cuentas por cobrar', 'RECEIVABLES'),
    (CAST('b1000000-0000-0000-0000-000000000020' AS UUID), 'PAYABLES_VIEW', 'Ver cuentas por pagar', 'PAYABLES'),
    (CAST('b1000000-0000-0000-0000-000000000021' AS UUID), 'PAYABLES_MANAGE', 'Gestionar cuentas por pagar', 'PAYABLES'),
    (CAST('b1000000-0000-0000-0000-000000000022' AS UUID), 'PUC_VIEW', 'Ver plan de cuentas', 'PUC'),
    (CAST('b1000000-0000-0000-0000-000000000023' AS UUID), 'PUC_MANAGE', 'Administrar plan de cuentas', 'PUC'),
    (CAST('b1000000-0000-0000-0000-000000000024' AS UUID), 'ACCOUNTING_VIEW', 'Ver contabilidad y comprobantes', 'ACCOUNTING'),
    (CAST('b1000000-0000-0000-0000-000000000025' AS UUID), 'ACCOUNTING_CREATE', 'Registrar comprobantes contables', 'ACCOUNTING'),
    (CAST('b1000000-0000-0000-0000-000000000026' AS UUID), 'ACCOUNTING_CLOSE', 'Cierres y anulaciones contables', 'ACCOUNTING'),
    (CAST('b1000000-0000-0000-0000-000000000027' AS UUID), 'REPORTS_VIEW', 'Ver reportes financieros', 'REPORTS'),
    (CAST('b1000000-0000-0000-0000-000000000028' AS UUID), 'REPORTS_EXPORT', 'Exportar reportes (PDF / Excel)', 'REPORTS'),
    (CAST('b1000000-0000-0000-0000-000000000029' AS UUID), 'SETTINGS_VIEW', 'Ver configuración', 'SETTINGS'),
    (CAST('b1000000-0000-0000-0000-000000000030' AS UUID), 'SETTINGS_MANAGE', 'Modificar configuración', 'SETTINGS');

-- SUPER_ADMIN: todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT CAST('b0000000-0000-0000-0000-000000000001' AS UUID), p.id FROM permissions p;

-- ADMINISTRADOR: todo excepto gestión de accesos
INSERT INTO role_permissions (role_id, permission_id)
SELECT CAST('b0000000-0000-0000-0000-000000000002' AS UUID), p.id FROM permissions p
WHERE p.module NOT IN ('USERS', 'ROLES', 'PERMISSIONS');

-- Asignación inicial de SUPER_ADMIN por correo (solo asignación de datos, no regla de autorización)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, CAST('b0000000-0000-0000-0000-000000000001' AS UUID)
FROM users u
WHERE LOWER(u.email) = 'juliandrosalesp@gmail.com';

-- Usuarios existentes con rol legado conservan acceso funcional como ADMINISTRADOR
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, CAST('b0000000-0000-0000-0000-000000000002' AS UUID)
FROM users u
WHERE u.role IS NOT NULL
  AND LOWER(u.email) <> 'juliandrosalesp@gmail.com';
