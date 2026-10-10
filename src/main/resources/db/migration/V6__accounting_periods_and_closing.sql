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
-- Cuenta 5905: Ganancias y Pérdidas
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('59000000-0000-0000-0000-000000000001', '59', 'GANANCIAS Y PERDIDAS', 2, 'DEBIT', '50000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('59050000-0000-0000-0000-000000000001', '5905', 'GANANCIAS Y PERDIDAS', 3, 'DEBIT', '59000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('59050500-0000-0000-0000-000000000001', '590505', 'GANANCIAS Y PERDIDAS', 4, 'DEBIT', '59050000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

-- Grupo 36: Resultados del Ejercicio y Cuentas de Utilidad (3605) / Pérdida (3610)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('36000000-0000-0000-0000-000000000001', '36', 'RESULTADOS DEL EJERCICIO', 2, 'CREDIT', '30000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('36050000-0000-0000-0000-000000000001', '3605', 'UTILIDAD DEL EJERCICIO', 3, 'CREDIT', '36000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('36050500-0000-0000-0000-000000000001', '360505', 'UTILIDAD DEL EJERCICIO', 4, 'CREDIT', '36050000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('36100000-0000-0000-0000-000000000001', '3610', 'PERDIDA DEL EJERCICIO', 3, 'DEBIT', '36000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('36100500-0000-0000-0000-000000000001', '361005', 'PERDIDA DEL EJERCICIO', 4, 'DEBIT', '36100000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

-- Clase 7: Costos de Producción
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('70000000-0000-0000-0000-000000000001', '7', 'COSTOS DE PRODUCCION O DE OPERACION', 1, 'DEBIT', NULL, FALSE, TRUE, 'SYSTEM');
