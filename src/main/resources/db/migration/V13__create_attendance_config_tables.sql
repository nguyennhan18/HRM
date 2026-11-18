-- Provision btree_gist beforehand if the migration role cannot CREATE EXTENSION.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

CREATE TABLE shifts (
    id uuid CONSTRAINT pk_shifts PRIMARY KEY,
    code varchar(20) NOT NULL CONSTRAINT uq_shifts_code UNIQUE,
    name varchar(200) NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    break_start time,
    break_end time,
    overnight boolean NOT NULL DEFAULT false,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_shifts_code CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    CONSTRAINT ck_shifts_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_shifts_time_range CHECK (
        (NOT overnight AND end_time > start_time)
        OR (overnight AND end_time < start_time)
    ),
    CONSTRAINT ck_shifts_break CHECK (
        (break_start IS NULL AND break_end IS NULL)
        OR (
            break_start IS NOT NULL AND break_end IS NOT NULL
            AND (
                (NOT overnight AND break_start >= start_time AND break_end <= end_time AND break_end > break_start)
                OR (
                    overnight AND (
                        (break_start >= start_time AND break_end > break_start)
                        OR (break_end <= end_time AND break_end > break_start)
                        OR (break_start >= start_time AND break_end <= end_time)
                    )
                )
            )
        )
    )
);

CREATE UNIQUE INDEX uq_shifts_single_default ON shifts (is_default) WHERE is_default = true;

CREATE TABLE attendance_policies (
    id uuid CONSTRAINT pk_attendance_policies PRIMARY KEY,
    code varchar(20) NOT NULL CONSTRAINT uq_attendance_policies_code UNIQUE,
    name varchar(200) NOT NULL,
    late_grace_minutes integer NOT NULL,
    early_leave_grace_minutes integer NOT NULL,
    rounding_step_minutes integer NOT NULL,
    rounding_mode varchar(20) NOT NULL,
    half_day_threshold_minutes integer NOT NULL,
    min_full_day_minutes integer NOT NULL,
    weekday_overtime_rate numeric(5,2) NOT NULL,
    weekend_overtime_rate numeric(5,2) NOT NULL,
    holiday_overtime_rate numeric(5,2) NOT NULL,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_attendance_policies_code CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    CONSTRAINT ck_attendance_policies_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_attendance_policies_grace CHECK (
        late_grace_minutes >= 0 AND late_grace_minutes <= 720
        AND early_leave_grace_minutes >= 0 AND early_leave_grace_minutes <= 720
    ),
    CONSTRAINT ck_attendance_policies_rounding_mode CHECK (
        rounding_mode IN ('NONE', 'NEAREST', 'DOWN', 'UP')
    ),
    CONSTRAINT ck_attendance_policies_rounding_step CHECK (
        (rounding_mode = 'NONE' AND rounding_step_minutes >= 0 AND rounding_step_minutes <= 240)
        OR (rounding_mode <> 'NONE' AND rounding_step_minutes > 0 AND rounding_step_minutes <= 240)
    ),
    CONSTRAINT ck_attendance_policies_work_minutes CHECK (
        half_day_threshold_minutes > 0
        AND min_full_day_minutes >= half_day_threshold_minutes
        AND min_full_day_minutes <= 1440
    ),
    CONSTRAINT ck_attendance_policies_overtime_rates CHECK (
        weekday_overtime_rate >= 1.00 AND weekday_overtime_rate <= 10.00
        AND weekend_overtime_rate >= 1.00 AND weekend_overtime_rate <= 10.00
        AND holiday_overtime_rate >= 1.00 AND holiday_overtime_rate <= 10.00
    )
);

CREATE UNIQUE INDEX uq_attendance_policies_single_default
    ON attendance_policies (is_default) WHERE is_default = true;

CREATE TABLE holidays (
    id uuid CONSTRAINT pk_holidays PRIMARY KEY,
    holiday_date date NOT NULL CONSTRAINT uq_holidays_date UNIQUE,
    name varchar(200) NOT NULL,
    multiplier numeric(5,2) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_holidays_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_holidays_multiplier CHECK (multiplier >= 1.00 AND multiplier <= 10.00)
);

CREATE TABLE employee_shifts (
    id uuid CONSTRAINT pk_employee_shifts PRIMARY KEY,
    employee_id uuid NOT NULL CONSTRAINT fk_employee_shifts_employee REFERENCES employees(id) ON DELETE RESTRICT,
    shift_id uuid NOT NULL CONSTRAINT fk_employee_shifts_shift REFERENCES shifts(id) ON DELETE RESTRICT,
    assignment_type varchar(20) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_employee_shifts_type CHECK (assignment_type IN ('DEFAULT', 'WEEKLY', 'DAILY')),
    CONSTRAINT ck_employee_shifts_dates CHECK (
        (assignment_type = 'DEFAULT' AND (end_date IS NULL OR end_date >= start_date))
        OR (assignment_type = 'WEEKLY' AND end_date IS NOT NULL AND end_date >= start_date AND (end_date - start_date) <= 6)
        OR (assignment_type = 'DAILY' AND end_date IS NOT NULL AND end_date = start_date)
    ),
    CONSTRAINT ex_employee_shifts_overlap EXCLUDE USING gist (
        employee_id WITH =,
        assignment_type WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    )
);

CREATE INDEX idx_employee_shifts_employee_dates ON employee_shifts(employee_id, start_date, end_date);
CREATE INDEX idx_employee_shifts_shift ON employee_shifts(shift_id);
