CREATE TABLE attendance_records (
    id uuid CONSTRAINT pk_attendance_records PRIMARY KEY,
    employee_id uuid NOT NULL CONSTRAINT fk_attendance_records_employee REFERENCES employees(id) ON DELETE RESTRICT,
    shift_id uuid NOT NULL CONSTRAINT fk_attendance_records_shift REFERENCES shifts(id) ON DELETE RESTRICT,
    work_date date NOT NULL,
    check_in_at timestamptz,
    check_in_source varchar(20),
    check_out_at timestamptz,
    check_out_source varchar(20),
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_attendance_records_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT ck_attendance_records_check_in_source CHECK (
        (check_in_at IS NULL AND check_in_source IS NULL)
        OR (check_in_at IS NOT NULL AND check_in_source IN ('WEB', 'QR', 'GPS'))
    ),
    CONSTRAINT ck_attendance_records_check_out_source CHECK (
        (check_out_at IS NULL AND check_out_source IS NULL)
        OR (check_out_at IS NOT NULL AND check_in_at IS NOT NULL AND check_out_source IN ('WEB', 'QR', 'GPS'))
    ),
    CONSTRAINT ck_attendance_records_time_order CHECK (
        check_out_at IS NULL OR (check_in_at IS NOT NULL AND check_out_at >= check_in_at)
    ),
    CONSTRAINT ck_attendance_records_status CHECK (
        status IN (
            'PRESENT',
            'HALF_DAY',
            'LATE',
            'EARLY_LEAVE',
            'ON_LEAVE',
            'HOLIDAY',
            'ABSENT',
            'MISSING_CHECKOUT'
        )
    )
);

CREATE INDEX idx_attendance_records_employee_open
    ON attendance_records (employee_id, work_date DESC)
    WHERE check_in_at IS NOT NULL AND check_out_at IS NULL;

CREATE INDEX idx_attendance_records_shift ON attendance_records (shift_id);
