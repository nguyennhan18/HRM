CREATE TABLE departments (
    id uuid CONSTRAINT pk_departments PRIMARY KEY,
    code varchar(20) NOT NULL CONSTRAINT uq_departments_code UNIQUE,
    name varchar(200) NOT NULL,
    parent_id uuid CONSTRAINT fk_departments_parent REFERENCES departments(id) ON DELETE RESTRICT,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_departments_code CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    CONSTRAINT ck_departments_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_departments_not_self_parent CHECK (parent_id <> id)
);

CREATE TABLE positions (
    id uuid CONSTRAINT pk_positions PRIMARY KEY,
    code varchar(20) NOT NULL CONSTRAINT uq_positions_code UNIQUE,
    name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_positions_code CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    CONSTRAINT ck_positions_name CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_departments_parent ON departments(parent_id);
