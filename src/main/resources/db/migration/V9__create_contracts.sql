-- Provision btree_gist beforehand if the migration role cannot CREATE EXTENSION.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
CREATE TABLE contracts (
    id uuid CONSTRAINT pk_contracts PRIMARY KEY,
    employee_id uuid NOT NULL CONSTRAINT fk_contracts_employee REFERENCES employees(id) ON DELETE RESTRICT,
    type varchar(20) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    base_salary numeric(18,0) NOT NULL,
    allowance numeric(18,0) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    deleted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_contracts_type CHECK (type IN ('PROBATION','FIXED_TERM','INDEFINITE')),
    CONSTRAINT ck_contracts_status CHECK (status IN ('DRAFT','ACTIVE','TERMINATED')),
    CONSTRAINT ck_contracts_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_contracts_open_end CHECK (end_date IS NOT NULL OR type = 'INDEFINITE'),
    CONSTRAINT ck_contracts_salary CHECK (base_salary >= 0 AND allowance >= 0),
    CONSTRAINT ex_contracts_effective_overlap EXCLUDE USING gist (
        employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (deleted_at IS NULL AND status <> 'DRAFT')
);
CREATE INDEX idx_contracts_employee ON contracts(employee_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_expiring ON contracts(end_date, id) WHERE deleted_at IS NULL AND status = 'ACTIVE';
