-- ==============================================================================
-- V9__loans_installments_payments.sql
-- Préstamos a asociados (amortización bajo saldo de capital), cuotas y abonos.
-- Datos de asociado en terceros.
-- ==============================================================================

-- 1. Datos de asociado sobre la tabla de terceros existente
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS id_type VARCHAR(40);
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS classification VARCHAR(30);
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS is_associate BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS associate_code VARCHAR(30);
ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS status VARCHAR(15) DEFAULT 'ACTIVE' NOT NULL;

CREATE INDEX IF NOT EXISTS idx_third_parties_associate ON third_parties(is_associate);

-- 2. Cuentas PUC requeridas por la cartera de préstamos (solo si no existen)
INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('13650000-0000-0000-0000-000000000001' AS UUID), '1365', 'CUENTAS POR COBRAR A TRABAJADORES Y ASOCIADOS', 3, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '13'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '1365')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '13');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('13650500-0000-0000-0000-000000000001' AS UUID), '136505', 'PRESTAMOS A ASOCIADOS', 4, 'DEBIT',
       (SELECT id FROM accounts WHERE code = '1365'), TRUE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '136505')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '1365');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('42000000-0000-0000-0000-000000000001' AS UUID), '42', 'NO OPERACIONALES', 2, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '4'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '42')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '4');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('42100000-0000-0000-0000-000000000001' AS UUID), '4210', 'FINANCIEROS', 3, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '42'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '4210')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '42');

INSERT INTO accounts (id, code, name, level, nature, parent_id, requires_third_party, is_active, created_by)
SELECT CAST('42100500-0000-0000-0000-000000000001' AS UUID), '421005', 'INTERESES SOBRE PRESTAMOS A ASOCIADOS', 4, 'CREDIT',
       (SELECT id FROM accounts WHERE code = '4210'), FALSE, TRUE, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE code = '421005')
  AND EXISTS (SELECT 1 FROM accounts WHERE code = '4210');

-- 3. Préstamos
CREATE TABLE IF NOT EXISTS loans (
    id UUID PRIMARY KEY,
    loan_number VARCHAR(30) NOT NULL UNIQUE,
    third_party_id UUID NOT NULL REFERENCES third_parties(id) ON DELETE RESTRICT,
    principal_amount NUMERIC(18, 2) NOT NULL CHECK (principal_amount > 0),
    monthly_rate NUMERIC(9, 6) NOT NULL CHECK (monthly_rate >= 0),
    term_months INT NOT NULL CHECK (term_months > 0),
    amortization_type VARCHAR(30) NOT NULL,
    disbursement_date DATE NOT NULL,
    first_payment_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    outstanding_principal NUMERIC(18, 2) NOT NULL,
    loan_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    interest_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    disbursement_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    disbursement_voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    legacy BOOLEAN DEFAULT FALSE NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_loans_party ON loans(third_party_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON loans(status);

-- 4. Cuotas (plan de pagos persistido)
CREATE TABLE IF NOT EXISTS loan_installments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    opening_balance NUMERIC(18, 2) NOT NULL,
    principal_amount NUMERIC(18, 2) NOT NULL,
    interest_amount NUMERIC(18, 2) NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL,
    closing_balance NUMERIC(18, 2) NOT NULL,
    principal_paid NUMERIC(18, 2) DEFAULT 0 NOT NULL,
    interest_paid NUMERIC(18, 2) DEFAULT 0 NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_date DATE,
    CONSTRAINT uq_loan_installment UNIQUE (loan_id, installment_number)
);

CREATE INDEX IF NOT EXISTS idx_installments_loan ON loan_installments(loan_id);
CREATE INDEX IF NOT EXISTS idx_installments_due ON loan_installments(due_date);

-- 5. Abonos (cada fila asocia un pago a asociado + préstamo + cuota)
CREATE TABLE IF NOT EXISTS loan_payments (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL,
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    installment_id UUID NOT NULL REFERENCES loan_installments(id) ON DELETE RESTRICT,
    third_party_id UUID NOT NULL REFERENCES third_parties(id) ON DELETE RESTRICT,
    payment_date DATE NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    principal_applied NUMERIC(18, 2) NOT NULL,
    interest_applied NUMERIC(18, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    cash_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    voucher_id UUID REFERENCES journal_vouchers(id) ON DELETE SET NULL,
    reference VARCHAR(100),
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_loan_payments_loan ON loan_payments(loan_id);
CREATE INDEX IF NOT EXISTS idx_loan_payments_party ON loan_payments(third_party_id);
CREATE INDEX IF NOT EXISTS idx_loan_payments_date ON loan_payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_loan_payments_receipt ON loan_payments(receipt_number);
