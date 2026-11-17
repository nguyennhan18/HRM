CREATE SEQUENCE employee_code_seq AS bigint MINVALUE 1 MAXVALUE 999999 NO CYCLE;

CREATE TABLE employees (
    id uuid CONSTRAINT pk_employees PRIMARY KEY,
    code varchar(8) NOT NULL CONSTRAINT uq_employees_code UNIQUE,
    full_name varchar(200) NOT NULL,
    date_of_birth date,
    gender varchar(10),
    personal_email varchar(254),
    company_email varchar(254) CONSTRAINT uq_employees_company_email UNIQUE,
    phone varchar(30),
    address varchar(1000),
    cccd_hash varchar(64) CONSTRAINT uq_employees_cccd_hash UNIQUE,
    cccd_last_four varchar(4),
    bank_account_hash varchar(64),
    bank_account_last_four varchar(4),
    department_id uuid CONSTRAINT fk_employees_department REFERENCES departments(id) ON DELETE RESTRICT,
    position_id uuid CONSTRAINT fk_employees_position REFERENCES positions(id) ON DELETE RESTRICT,
    manager_id uuid CONSTRAINT fk_employees_manager REFERENCES employees(id) ON DELETE RESTRICT,
    user_id uuid CONSTRAINT uq_employees_user UNIQUE
        CONSTRAINT fk_employees_user REFERENCES users(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    start_date date,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_employees_code CHECK (code ~ '^NV[0-9]{6}$' AND code <> 'NV000000'),
    CONSTRAINT ck_employees_full_name CHECK (length(trim(full_name)) > 0),
    CONSTRAINT ck_employees_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_employees_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ON_LEAVE_LONG', 'TERMINATED')),
    CONSTRAINT ck_employees_company_email CHECK (
        company_email = lower(trim(company_email)) AND length(company_email) > 0),
    CONSTRAINT ck_employees_not_self_manager CHECK (manager_id <> id),
    CONSTRAINT ck_employees_cccd CHECK (
        (cccd_hash IS NULL AND cccd_last_four IS NULL) OR
        (cccd_hash IS NOT NULL AND cccd_hash ~ '^[0-9a-f]{64}$'
            AND cccd_last_four IS NOT NULL AND cccd_last_four ~ '^[0-9]{4}$')),
    CONSTRAINT ck_employees_bank_account CHECK (
        (bank_account_hash IS NULL AND bank_account_last_four IS NULL) OR
        (bank_account_hash IS NOT NULL AND bank_account_hash ~ '^[0-9a-f]{64}$'
            AND bank_account_last_four IS NOT NULL AND bank_account_last_four ~ '^[0-9]{4}$'))
);

CREATE INDEX idx_employees_department_status ON employees(department_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_employees_position ON employees(position_id);
CREATE INDEX idx_employees_manager ON employees(manager_id);
CREATE INDEX idx_employees_bank_account_hash ON employees(bank_account_hash);
