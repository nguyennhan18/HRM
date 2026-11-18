-- Sample reference defaults approved in T3.1 (Option 1A). Values are configurable in DB.
INSERT INTO shifts (
    id, code, name, start_time, end_time, break_start, break_end,
    overnight, is_default, created_at, updated_at, created_by, updated_by, version
) VALUES
    (
        '019c9e00-0000-7000-8000-000000000301',
        'HC',
        'Ca hành chính ngày (Mẫu)',
        '08:00:00',
        '17:00:00',
        '12:00:00',
        '13:00:00',
        false,
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        'system',
        'system',
        0
    ),
    (
        '019c9e00-0000-7000-8000-000000000302',
        'NIGHT',
        'Ca đêm (Mẫu)',
        '22:00:00',
        '06:00:00',
        '02:00:00',
        '03:00:00',
        true,
        false,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        'system',
        'system',
        0
    );

INSERT INTO attendance_policies (
    id, code, name,
    late_grace_minutes, early_leave_grace_minutes,
    rounding_step_minutes, rounding_mode,
    half_day_threshold_minutes, min_full_day_minutes,
    weekday_overtime_rate, weekend_overtime_rate, holiday_overtime_rate,
    is_default, created_at, updated_at, created_by, updated_by, version
) VALUES (
    '019c9e00-0000-7000-8000-000000000311',
    'DEFAULT',
    'Chính sách chấm công mặc định (Mẫu)',
    15,
    0,
    15,
    'NEAREST',
    240,
    480,
    1.50,
    2.00,
    3.00,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system',
    0
);
