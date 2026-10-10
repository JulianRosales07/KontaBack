-- ==============================================================================
-- V6__accounting_periods_and_closing.sql
-- Módulo de Cierre Contable: Periodos Contables, Cuentas de Resultado y Cierre Anual
-- ==============================================================================

-- 1. Tabla: accounting_periods (Periodos Contables Mensuales)
CREATE TABLE IF NOT EXISTS accounting_periods (
    id UUID PRIMARY KEY,
    period_year INT NOT NULL,
    period_month INT NOT NULL CHECK (period_month >= 1 AND period_month <= 12),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_period_year_month UNIQUE (period_year, period_month)
);

CREATE INDEX IF NOT EXISTS idx_period_year_month ON accounting_periods(period_year, period_month);
CREATE INDEX IF NOT EXISTS idx_period_status ON accounting_periods(status);

-- 2. Cuentas PUC para el Cierre de Cuentas de Resultado (Clases 4, 5, 6, 7) y Traslado a Patrimonio
-- Idempotente: cada cuenta se crea solo si su código no existe, y la cuenta padre se busca por código
-- (en bases con el PUC completo ya cargado, estas cuentas existen con otros IDs).

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('59000000-0000-0000-0000-000000000001' AS UUID), '59', 'GANANCIAS Y PERDIDAS', 2, 'DEBIT', (SELECT id FROM accounts WHERE code = '5'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '59')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('59050000-0000-0000-0000-000000000001' AS UUID), '5905', 'GANANCIAS Y PERDIDAS', 3, 'DEBIT', (SELECT id FROM accounts WHERE code = '59'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '5905')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('59050500-0000-0000-0000-000000000001' AS UUID), '590505', 'GANANCIAS Y PERDIDAS', 4, 'DEBIT', (SELECT id FROM accounts WHERE code = '5905'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '590505')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('36000000-0000-0000-0000-000000000001' AS UUID), '36', 'RESULTADOS DEL EJERCICIO', 2, 'CREDIT', (SELECT id FROM accounts WHERE code = '3'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '36')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('36050000-0000-0000-0000-000000000001' AS UUID), '3605', 'UTILIDAD DEL EJERCICIO', 3, 'CREDIT', (SELECT id FROM accounts WHERE code = '36'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '3605')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('36050500-0000-0000-0000-000000000001' AS UUID), '360505', 'UTILIDAD DEL EJERCICIO', 4, 'CREDIT', (SELECT id FROM accounts WHERE code = '3605'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '360505')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('36100000-0000-0000-0000-000000000001' AS UUID), '3610', 'PERDIDA DEL EJERCICIO', 3, 'DEBIT', (SELECT id FROM accounts WHERE code = '36'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '3610')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('36100500-0000-0000-0000-000000000001' AS UUID), '361005', 'PERDIDA DEL EJERCICIO', 4, 'DEBIT', (SELECT id FROM accounts WHERE code = '3610'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '361005')
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('70000000-0000-0000-0000-000000000001' AS UUID), '7', 'COSTOS DE PRODUCCION O DE OPERACION', 1, 'DEBIT', NULL, FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '7')
ON CONFLICT DO NOTHING;
