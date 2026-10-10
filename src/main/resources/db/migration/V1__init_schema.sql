-- V1__init_schema.sql
-- Accounting Service Database Schema

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ROLE_ADMIN', 'ROLE_CONTADOR', 'ROLE_AUXILIAR')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    level INTEGER NOT NULL,
    nature VARCHAR(10) NOT NULL CHECK (nature IN ('DEBIT', 'CREDIT')),
    parent_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    requires_third_party BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE third_parties (
    id UUID PRIMARY KEY,
    identification_number VARCHAR(30) NOT NULL UNIQUE,
    dv VARCHAR(2),
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(30),
    type VARCHAR(15) NOT NULL CHECK (type IN ('NATURAL', 'JURIDICA')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE journal_vouchers (
    id UUID PRIMARY KEY,
    voucher_number VARCHAR(50) NOT NULL UNIQUE,
    voucher_type VARCHAR(30) NOT NULL CHECK (voucher_type IN ('INGRESO', 'EGRESO', 'NOTA_CONTABLE', 'APERTURA')),
    issue_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'POSTED', 'VOIDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL REFERENCES journal_vouchers(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    third_party_id UUID REFERENCES third_parties(id) ON DELETE RESTRICT,
    reference_number VARCHAR(50),
    description VARCHAR(500),
    debit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (debit >= 0),
    credit NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (credit >= 0)
);

-- Performance and Query Optimization Indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_accounts_code ON accounts(code);
CREATE INDEX idx_accounts_parent ON accounts(parent_id);
CREATE INDEX idx_third_parties_id_num ON third_parties(identification_number);
CREATE INDEX idx_vouchers_date ON journal_vouchers(issue_date);
CREATE INDEX idx_vouchers_status ON journal_vouchers(status);
CREATE INDEX idx_vouchers_type ON journal_vouchers(voucher_type);
CREATE INDEX idx_entries_voucher ON journal_entries(voucher_id);
CREATE INDEX idx_entries_account ON journal_entries(account_id);
CREATE INDEX idx_entries_third_party ON journal_entries(third_party_id);
