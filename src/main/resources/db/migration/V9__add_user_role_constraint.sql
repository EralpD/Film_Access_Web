UPDATE users
SET role = UPPER(TRIM(role));

ALTER TABLE users
    ADD CONSTRAINT ck_users_role
    CHECK (role IN ('USER', 'ADMIN'));
