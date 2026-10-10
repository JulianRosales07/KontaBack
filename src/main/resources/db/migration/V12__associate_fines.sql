-- ==============================================================================
-- V12__associate_fines.sql
-- Multas de asociados (inasistencia a reuniones y otras que defina la asociación).
--   * fine_types: catálogo de multas con su valor (la de inasistencia vale $5.000 y se pueden crear más).
--   * associate_fines: multa impuesta a un asociado. Mientras está PENDIENTE no afecta la contabilidad;
--     al pagarse se contabiliza como ingreso 429510 (Multas e inasistencia), igual que la columna MULTAS
--     de la planilla del informe mensual.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS fine_types (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(300),
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS associate_fines (
    id UUID PRIMARY KEY,
    third_party_id UUID NOT NULL REFERENCES third_parties(id) ON DELETE CASCADE,
    fine_type_id UUID NOT NULL REFERENCES fine_types(id) ON DELETE RESTRICT,
    fine_date DATE NOT NULL,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    description VARCHAR(300),
    status VARCHAR(15) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'VOIDED')),
    paid_date DATE,
    receipt_number VARCHAR(50),
    voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    void_reason VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_fines_party ON associate_fines(third_party_id);
CREATE INDEX IF NOT EXISTS idx_fines_status ON associate_fines(status);
CREATE INDEX IF NOT EXISTS idx_fines_date ON associate_fines(fine_date);

-- Multa base: inasistencia a reunión, $5.000
INSERT INTO fine_types (id, name, description, amount, is_active, created_by)
SELECT CAST('f1000000-0000-0000-0000-000000000001' AS UUID), 'Inasistencia a reunión',
       'Multa por no asistir a la reunión o asamblea de asociados', 5000.0000, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM fine_types WHERE name = 'Inasistencia a reunión');

-- Permisos del módulo
INSERT INTO permissions (id, code, name, module) VALUES
    (CAST('b1000000-0000-0000-0000-000000000040' AS UUID), 'FINES_VIEW', 'Ver multas de asociados', 'FINES'),
    (CAST('b1000000-0000-0000-0000-000000000041' AS UUID), 'FINES_MANAGE', 'Registrar, cobrar y condonar multas; configurar tipos de multa', 'FINES')
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN y ADMINISTRADOR reciben los permisos del módulo
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMINISTRADOR') AND p.code IN ('FINES_VIEW', 'FINES_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
