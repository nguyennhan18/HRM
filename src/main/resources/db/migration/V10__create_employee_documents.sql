CREATE TABLE employee_documents (
    id uuid CONSTRAINT pk_employee_documents PRIMARY KEY,
    employee_id uuid NOT NULL CONSTRAINT fk_employee_documents_employee REFERENCES employees(id) ON DELETE RESTRICT,
    type varchar(20) NOT NULL,
    original_name varchar(255) NOT NULL,
    stored_name varchar(36) NOT NULL CONSTRAINT uq_employee_documents_stored_name UNIQUE,
    size bigint NOT NULL,
    content_type varchar(50) NOT NULL,
    checksum varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_employee_documents_type CHECK (type IN ('CV','CERTIFICATE','OTHER')),
    CONSTRAINT ck_employee_documents_size CHECK (size > 0 AND size <= 10485760),
    CONSTRAINT ck_employee_documents_mime CHECK (content_type IN ('application/pdf','image/jpeg','image/png')),
    CONSTRAINT ck_employee_documents_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_employee_documents_name CHECK (length(original_name) > 0),
    CONSTRAINT ck_employee_documents_stored_name CHECK (stored_name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
);
CREATE INDEX idx_employee_documents_employee ON employee_documents(employee_id);
