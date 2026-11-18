ALTER TABLE employees
    ADD COLUMN termination_date date,
    ADD COLUMN termination_reason varchar(500),
    ADD CONSTRAINT ck_employees_termination_fields CHECK (
        (termination_date IS NULL AND termination_reason IS NULL) OR
        (termination_date IS NOT NULL AND termination_reason IS NOT NULL
            AND length(trim(termination_reason)) > 0 AND status = 'TERMINATED')
    ),
    ADD CONSTRAINT ck_employees_termination_date CHECK (
        termination_date IS NULL OR start_date IS NULL OR termination_date >= start_date
    );
