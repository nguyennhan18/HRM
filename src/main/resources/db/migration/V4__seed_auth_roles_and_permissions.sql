INSERT INTO roles (id, code, created_at, updated_at, created_by, updated_by, version)
VALUES
    ('019c9e00-0000-7000-8000-000000000001', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000002', 'HR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000003', 'MANAGER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000004', 'EMPLOYEE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0);

INSERT INTO permissions (id, code, created_at, updated_at, created_by, updated_by, version)
VALUES
    ('019c9e00-0000-7000-8000-000000000101', 'user:manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000102', 'role:manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000103', 'employee:read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000104', 'employee:write', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000105', 'contract:read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000106', 'contract:write', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000107', 'payroll:read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000108', 'attendance:check', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000109', 'attendance:read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000110', 'attendance:write', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000111', 'leave:request', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000112', 'overtime:request', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000113', 'attendance_adjustment:request', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000114', 'leave:approve_level1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000115', 'overtime:approve_level1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000116', 'attendance_adjustment:approve_level1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000117', 'leave:approve_level2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000118', 'overtime:approve_level2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000119', 'attendance_adjustment:approve_level2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000120', 'attendance:lock', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000121', 'payroll:calculate', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000122', 'payroll:approve', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0),
    ('019c9e00-0000-7000-8000-000000000123', 'audit:read', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'ADMIN'
UNION ALL
SELECT role.id, permission.id FROM roles role JOIN permissions permission ON permission.code IN (
    'employee:read', 'employee:write', 'contract:read', 'contract:write', 'payroll:read',
    'attendance:read', 'attendance:write', 'leave:approve_level2', 'overtime:approve_level2',
    'attendance_adjustment:approve_level2', 'attendance:lock', 'payroll:calculate'
) WHERE role.code = 'HR'
UNION ALL
SELECT role.id, permission.id FROM roles role JOIN permissions permission ON permission.code IN (
    'employee:read', 'attendance:check', 'attendance:read', 'leave:request', 'overtime:request',
    'attendance_adjustment:request', 'leave:approve_level1', 'overtime:approve_level1',
    'attendance_adjustment:approve_level1'
) WHERE role.code = 'MANAGER'
UNION ALL
SELECT role.id, permission.id FROM roles role JOIN permissions permission ON permission.code IN (
    'employee:read', 'payroll:read', 'attendance:check', 'attendance:read', 'leave:request',
    'overtime:request', 'attendance_adjustment:request'
) WHERE role.code = 'EMPLOYEE';
