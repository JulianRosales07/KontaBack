-- ==============================================================================
-- V11__association_report_accounts.sql
-- Mapeo concepto -> cuenta PUC validado por la contadora (informe mensual abril 2026):
--   * A.EXTRA pasa a pasivo (289510), BINGO y FDE pasan a ingreso, PREEXEQUIALES pasivo para terceros (281505),
--     MULTAS y ARRIENDOS ingreso.
--   * Corrige la creación de 4210/421005 en bases nuevas (V9 comparaba 4210 consigo misma).
--   * Registro mínimo de inversiones (CDT).
-- Todas las inserciones son idempotentes y solo se ejecutan si existe la cuenta padre; en bases donde falte
-- el padre, AccountProvisioningService crea la cadena completa cuando se necesita.
-- ==============================================================================

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000001' AS UUID), '4210', 'FINANCIEROS', 3, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '42'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '4210')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '42')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000002' AS UUID), '421005', 'INTERESES SOBRE PRESTAMOS A ASOCIADOS', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '4210'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '421005')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '4210')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000003' AS UUID), '514520', 'EQUIPO DE OFICINA', 4, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '5145'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '514520')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '5145')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000004' AS UUID), '289510', 'APORTES EXTRAORDINARIOS DE ASOCIADOS', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '2895'), TRUE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '289510')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '2895')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000005' AS UUID), '370505', 'EXCEDENTES DE EJERCICIOS ANTERIORES', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '3705'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '370505')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '3705')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000006' AS UUID), '381005', 'VALORIZACION CASA SEDE', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '3810'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '381005')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '3810')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000007' AS UUID), '381010', 'VALORIZACION MUEBLES Y EQUIPO DE OFICINA', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '3810'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '381010')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '3810')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000008' AS UUID), '429505', 'FONDO DE DESTINACION ESPECIFICA', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '4295'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '429505')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '4295')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000009' AS UUID), '429510', 'MULTAS E INASISTENCIA', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '4295'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '429510')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '4295')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000010' AS UUID), '41709501', 'INGRESOS POR BINGOS', 5, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '417095'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '41709501')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '417095')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000011' AS UUID), '41709502', 'PLATOS Y EVENTOS', 5, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '417095'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '41709502')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '417095')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000012' AS UUID), '41709503', 'FIESTAS', 5, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '417095'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '41709503')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '417095')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000013' AS UUID), '41709504', 'RIFAS', 5, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '417095'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '41709504')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '417095')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000014' AS UUID), '42109501', 'RENDIMIENTOS CUENTA DE AHORROS', 5, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '421095'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '42109501')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '421095')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000015' AS UUID), '51959501', 'COLABORACION PERSONAL ADMINISTRATIVO', 5, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '519595'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '51959501')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '519595')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000016' AS UUID), '51959502', 'PREMIOS BINGO', 5, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '519595'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '51959502')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '519595')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('e1100000-0000-0000-0000-000000000017' AS UUID), '53959501', 'ACTIVIDADES SOCIALES DE ASOCIADOS', 5, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '539595'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '53959501')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '539595')
ON CONFLICT DO NOTHING;

-- Renombres aprobados (el código no cambia)
UPDATE accounts SET name = 'FONDO DE SOLIDARIDAD', updated_at = CURRENT_TIMESTAMP WHERE code = '238025';
UPDATE accounts SET name = 'FONDO DE TURISMO Y RECREACION', updated_at = CURRENT_TIMESTAMP WHERE code = '238035';
UPDATE accounts SET name = 'PRESTAMOS A ASOCIADOS', updated_at = CURRENT_TIMESTAMP WHERE code = '136505';

-- Plantilla de cuota mensual: bingo y FDE son ingreso; el aporte extraordinario es un pasivo con el asociado
UPDATE quota_template_items
SET account_id = (SELECT id FROM accounts WHERE code = '41709501'), category = 'INCOME'
WHERE id = 'c1000000-0000-0000-0000-000000000005'
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '41709501');

UPDATE quota_template_items
SET account_id = (SELECT id FROM accounts WHERE code = '429505'), category = 'INCOME'
WHERE id = 'c1000000-0000-0000-0000-000000000006'
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '429505');

UPDATE quota_template_items
SET account_id = (SELECT id FROM accounts WHERE code = '289510'), category = 'SAVINGS'
WHERE id = 'c1000000-0000-0000-0000-000000000007'
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '289510');

-- Registro mínimo de inversiones (CDT y similares). El saldo contable vive en la cuenta PUC;
-- este registro guarda los datos del título para el reporte de inversiones.
CREATE TABLE IF NOT EXISTS investments (
    id UUID PRIMARY KEY,
    entity_name VARCHAR(150) NOT NULL,
    instrument_type VARCHAR(30) NOT NULL DEFAULT 'CDT',
    instrument_number VARCHAR(60),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    principal NUMERIC(18, 4) NOT NULL CHECK (principal >= 0),
    accrued_interest NUMERIC(18, 4) NOT NULL DEFAULT 0,
    annual_rate NUMERIC(9, 6),
    start_date DATE,
    maturity_date DATE,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_investments_account ON investments(account_id);
