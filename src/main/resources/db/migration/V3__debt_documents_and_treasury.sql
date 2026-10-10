-- V3__debt_documents_and_treasury.sql
-- Extension for Cartera (Billing & Expenses), Treasury, Colombian Tax Templates, and Additional PUC Accounts

-- 1. Additional Colombian PUC Accounts
-- Grupo 14 (Inventarios)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('14000000-0000-0000-0000-000000000001', '14', 'INVENTARIOS', 2, 'DEBIT', '10000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('14350000-0000-0000-0000-000000000001', '1435', 'MERCANCIAS NO FABRICADAS POR LA EMPRESA', 3, 'DEBIT', '14000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('14350500-0000-0000-0000-000000000001', '143505', 'MERCANCIAS GENERALES', 4, 'DEBIT', '14350000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- Anticipos y Saldos a Favor (Grupo 13)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('13550000-0000-0000-0000-000000000001', '1355', 'ANTICIPO DE IMPUESTOS Y CONTRIBUCIONES', 3, 'DEBIT', '13000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('13551500-0000-0000-0000-000000000001', '135515', 'RETENCION EN LA FUENTE A FAVOR', 4, 'DEBIT', '13550000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('13551800-0000-0000-0000-000000000001', '135518', 'IMPUESTO DE INDUSTRIA Y COMERCIO RETENIDO (A FAVOR)', 4, 'DEBIT', '13550000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- Retenciones por Pagar (Grupo 23)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23650000-0000-0000-0000-000000000001', '2365', 'RETENCION EN LA FUENTE POR PAGAR', 3, 'CREDIT', '23000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23654000-0000-0000-0000-000000000001', '236540', 'RETEFUENTE EN COMPRAS', 4, 'CREDIT', '23650000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23680000-0000-0000-0000-000000000001', '2368', 'IMPUESTO DE INDUSTRIA Y COMERCIO RETENIDO (POR PAGAR)', 3, 'CREDIT', '23000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('23680500-0000-0000-0000-000000000001', '236805', 'RETEICA POR PAGAR', 4, 'CREDIT', '23680000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- Impuestos Gravamenes y Tasas (Grupo 24)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('24000000-0000-0000-0000-000000000001', '24', 'IMPUESTOS GRAVAMENES Y TASAS', 2, 'CREDIT', '20000000-0000-0000-0000-000000000001', FALSE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('24080000-0000-0000-0000-000000000001', '2408', 'IMPUESTO SOBRE LAS VENTAS POR PAGAR', 3, 'CREDIT', '24000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('24080500-0000-0000-0000-000000000001', '240805', 'IVA GENERADO', 4, 'CREDIT', '24080000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('24081000-0000-0000-0000-000000000001', '240810', 'IVA DESCONTABLE', 4, 'DEBIT', '24080000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- Ingresos Operacionales Comercio (Nivel 4)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('41350500-0000-0000-0000-000000000001', '413505', 'VENTA DE MERCANCIAS', 4, 'CREDIT', '41350000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

-- Gastos Operacionales (Grupo 51)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('51350000-0000-0000-0000-000000000001', '5135', 'SERVICIOS', 3, 'DEBIT', '51000000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
VALUES ('51350500-0000-0000-0000-000000000001', '513505', 'SERVICIOS GENERALES', 4, 'DEBIT', '51350000-0000-0000-0000-000000000001', TRUE, TRUE, 'SYSTEM');


-- 2. Cartera: Tabla debt_documents (Cuentas por Cobrar y por Pagar)
CREATE TABLE debt_documents (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('RECEIVABLE', 'PAYABLE')),
    document_number VARCHAR(50) NOT NULL UNIQUE,
    third_party_id UUID NOT NULL REFERENCES third_parties(id) ON DELETE RESTRICT,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    subtotal NUMERIC(18, 4) NOT NULL CHECK (subtotal >= 0),
    tax_total NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    retention_total NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (retention_total >= 0),
    total_amount NUMERIC(18, 4) NOT NULL CHECK (total_amount >= 0),
    remaining_balance NUMERIC(18, 4) NOT NULL CHECK (remaining_balance >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_debt_doc_num ON debt_documents(document_number);
CREATE INDEX idx_debt_party ON debt_documents(third_party_id);
CREATE INDEX idx_debt_type ON debt_documents(type);
CREATE INDEX idx_debt_status ON debt_documents(status);
CREATE INDEX idx_debt_due_date ON debt_documents(due_date);


-- 3. Tesorería: Tabla payment_receipts (Recibos de Caja y Comprobantes de Egreso)
CREATE TABLE payment_receipts (
    id UUID PRIMARY KEY,
    payment_type VARCHAR(20) NOT NULL CHECK (payment_type IN ('CASH_IN', 'CASH_OUT')),
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    payment_date DATE NOT NULL,
    debt_document_id UUID REFERENCES debt_documents(id) ON DELETE RESTRICT,
    cash_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount_paid NUMERIC(18, 4) NOT NULL CHECK (amount_paid > 0),
    voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_receipt_num ON payment_receipts(receipt_number);
CREATE INDEX idx_receipt_type ON payment_receipts(payment_type);
CREATE INDEX idx_receipt_date ON payment_receipts(payment_date);
CREATE INDEX idx_receipt_doc ON payment_receipts(debt_document_id);
CREATE INDEX idx_receipt_cash_acc ON payment_receipts(cash_account_id);


-- 4. Tabla de Plantillas de Impuestos (tax_templates)
CREATE TABLE tax_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    tax_type VARCHAR(30) NOT NULL CHECK (tax_type IN ('IVA', 'RETEFUENTE', 'RETEICA', 'IMPOCONSUMO')),
    rate_percentage NUMERIC(8, 4) NOT NULL,
    sales_account_code VARCHAR(20) NOT NULL,
    purchase_account_code VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_tax_type ON tax_templates(tax_type);

-- Semilla de Plantillas Tributarias Colombianas
INSERT INTO tax_templates (id, name, tax_type, rate_percentage, sales_account_code, purchase_account_code, is_active, created_by)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'IVA General 19%', 'IVA', 19.0000, '240805', '240810', TRUE, 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000002', 'IVA Reducido 5%', 'IVA', 5.0000, '240805', '240810', TRUE, 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000003', 'Retefuente Compras 2.5%', 'RETEFUENTE', 2.5000, '135515', '236540', TRUE, 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000004', 'Retefuente Servicios 4%', 'RETEFUENTE', 4.0000, '135515', '2365', TRUE, 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000005', 'ReteICA Bogotá 0.966%', 'RETEICA', 0.9660, '135518', '236805', TRUE, 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000006', 'Impoconsumo 8%', 'IMPOCONSUMO', 8.0000, '240805', '513505', TRUE, 'SYSTEM');
