CREATE TABLE audit_logs (
    id uuid NOT NULL,
    actor varchar(100) NOT NULL,
    action varchar(100) NOT NULL,
    entity_type varchar(100) NOT NULL,
    entity_id uuid,
    before_json jsonb,
    after_json jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id varchar(100),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_audit_logs_action_not_blank CHECK (length(trim(action)) > 0),
    CONSTRAINT ck_audit_logs_entity_type_not_blank CHECK (length(trim(entity_type)) > 0)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_trace_id ON audit_logs (trace_id);
