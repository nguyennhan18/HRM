ALTER TABLE users ADD COLUMN must_change_password boolean;
UPDATE users SET must_change_password = false;
ALTER TABLE users ALTER COLUMN must_change_password SET NOT NULL;
ALTER TABLE users ALTER COLUMN must_change_password SET DEFAULT false;
