-- ==============================================================================
-- V5__associate_quotas_and_receipts.sql
-- Módulo de Cuotas y Recaudos para Asociaciones, Cooperativas y Fondos
-- ==============================================================================

-- 1. Tabla de Plantillas de Cuotas (Estructura de cobro mensual o periódico)
CREATE TABLE IF NOT EXISTS quota_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    total_amount NUMERIC(18, 4) NOT NULL CHECK (total_amount >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

-- 2. Items / Conceptos de la Plantilla de Cuota
CREATE TABLE IF NOT EXISTS quota_template_items (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES quota_templates(id) ON DELETE CASCADE,
    concept_name VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('INCOME', 'EQUITY_SAVING', 'SOCIAL_FUND')),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount >= 0),
    percentage NUMERIC(8, 4),
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_quota_item_template ON quota_template_items(template_id);
CREATE INDEX IF NOT EXISTS idx_quota_item_account ON quota_template_items(account_id);

-- 3. Tabla de Recaudos de Asociados (Recibo de Caja para el asociado)
CREATE TABLE IF NOT EXISTS associate_receipts (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    third_party_id UUID NOT NULL REFERENCES third_parties(id) ON DELETE RESTRICT,
    template_id UUID REFERENCES quota_templates(id) ON DELETE SET NULL,
    payment_date DATE NOT NULL,
    total_amount NUMERIC(18, 4) NOT NULL CHECK (total_amount > 0),
    cash_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_assoc_rc_party ON associate_receipts(third_party_id);
CREATE INDEX IF NOT EXISTS idx_assoc_rc_date ON associate_receipts(payment_date);
CREATE INDEX IF NOT EXISTS idx_assoc_rc_voucher ON associate_receipts(voucher_id);

-- 4. Detalle de Conceptos Recaudados en el Recibo
CREATE TABLE IF NOT EXISTS associate_receipt_items (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL REFERENCES associate_receipts(id) ON DELETE CASCADE,
    concept_name VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('INCOME', 'EQUITY_SAVING', 'SOCIAL_FUND')),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_assoc_rc_item_receipt ON associate_receipt_items(receipt_id);
CREATE INDEX IF NOT EXISTS idx_assoc_rc_item_account ON associate_receipt_items(account_id);

-- Cuentas PUC Requeridas para Asociaciones y Cuotas
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('41700000-0000-0000-0000-000000000001', '4170', 'OTRAS ACTIVIDADES DE SERVICIOS COMUNITARIOS', 3, 'CREDIT', '41000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('41701000-0000-0000-0000-000000000001', '417010', 'CUOTA DE ADMINISTRACION (SOSTENIMIENTO)', 4, 'CREDIT', '41700000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23800000-0000-0000-0000-000000000001', '2380', 'ACREEDORES VARIOS', 3, 'CREDIT', '23000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23802500-0000-0000-0000-000000000001', '238025', 'FONDO DE SOLIDARIDAD', 4, 'CREDIT', '23800000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23803500-0000-0000-0000-000000000001', '238035', 'TURISMO Y RECREACION', 4, 'CREDIT', '23800000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23809500-0000-0000-0000-000000000001', '238095', 'BINGO Y ACTIVIDADES ESPECIALES', 4, 'CREDIT', '23800000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23802000-0000-0000-0000-000000000001', '238020', 'FONDO DE DESTINACION ESPECIFICA', 4, 'CREDIT', '23800000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('31000000-0000-0000-0000-000000000001', '31', 'CAPITAL SOCIAL', 2, 'CREDIT', '30000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('31150000-0000-0000-0000-000000000001', '3115', 'APORTES SOCIALES', 3, 'CREDIT', '31000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('31150500-0000-0000-0000-000000000001', '311505', 'APORTES SOCIALES ORDINARIOS', 4, 'CREDIT', '31150000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('31151000-0000-0000-0000-000000000001', '311510', 'APORTE EXTRAORDINARIO', 4, 'CREDIT', '31150000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- 5. Semilla: Plantilla Estándar Cuota $50.000 COP
INSERT INTO quota_templates (id, name, description, total_amount, is_active, created_by)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'Cuota Ordinaria Mensual de Asociado ($50.000)',
    'Distribución de cuota mensual: $6.250 administración, $5.250 solidaridad, $10.000 aportes, $5.500 recreación, $2.000 bingo, $1.000 fondo específico y $20.000 aporte extraordinario.',
    50000.0000,
    TRUE,
    'SYSTEM'
);

-- Items de la plantilla de $50.000 enlazados con las cuentas del PUC
INSERT INTO quota_template_items (id, template_id, concept_name, category, account_id, amount, percentage, is_mandatory, display_order)
VALUES
    (
        'c1000000-0000-0000-0000-000000000001',
        'c0000000-0000-0000-0000-000000000001',
        'Cuota de Administración (Sostenimiento)',
        'INCOME',
        (SELECT id FROM accounts WHERE code = '417010'),
        6250.0000,
        12.5000,
        TRUE,
        1
    ),
    (
        'c1000000-0000-0000-0000-000000000002',
        'c0000000-0000-0000-0000-000000000001',
        'Fondo de Solidaridad',
        'SOCIAL_FUND',
        (SELECT id FROM accounts WHERE code = '238025'),
        5250.0000,
        10.5000,
        TRUE,
        2
    ),
    (
        'c1000000-0000-0000-0000-000000000003',
        'c0000000-0000-0000-0000-000000000001',
        'Aportes Sociales Ordinarios (Ahorro del Socio)',
        'EQUITY_SAVING',
        (SELECT id FROM accounts WHERE code = '311505'),
        10000.0000,
        20.0000,
        TRUE,
        3
    ),
    (
        'c1000000-0000-0000-0000-000000000004',
        'c0000000-0000-0000-0000-000000000001',
        'Turismo y Recreación',
        'SOCIAL_FUND',
        (SELECT id FROM accounts WHERE code = '238035'),
        5500.0000,
        11.0000,
        TRUE,
        4
    ),
    (
        'c1000000-0000-0000-0000-000000000005',
        'c0000000-0000-0000-0000-000000000001',
        'Bingo y Actividades Especiales',
        'SOCIAL_FUND',
        (SELECT id FROM accounts WHERE code = '238095'),
        2000.0000,
        4.0000,
        TRUE,
        5
    ),
    (
        'c1000000-0000-0000-0000-000000000006',
        'c0000000-0000-0000-0000-000000000001',
        'Fondo de Destinación Específica',
        'SOCIAL_FUND',
        (SELECT id FROM accounts WHERE code = '238020'),
        1000.0000,
        2.0000,
        TRUE,
        6
    ),
    (
        'c1000000-0000-0000-0000-000000000007',
        'c0000000-0000-0000-0000-000000000001',
        'Aporte Extraordinario (Ahorro Extra del Socio)',
        'EQUITY_SAVING',
        (SELECT id FROM accounts WHERE code = '311510'),
        20000.0000,
        40.0000,
        FALSE,
        7
    );
