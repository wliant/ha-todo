-- Demo accounts for local development and the dev environment only; this
-- location is excluded from the prod profile. Both passwords are 'password123'.
-- Versioned at 900+ to stay clear of the schema migration series.
INSERT INTO app_user (email, display_name, password_hash)
VALUES
    ('ana@example.com', 'Ana', '$2y$10$ca5oBvmXYQKrb9NIiHTdZOMdQE75fh/3/6A7RblMXxX9rRB038IYO'),
    ('ben@example.com', 'Ben', '$2y$10$cKKHBcb3NPtbmUMWGqVPRem4jKM3CfgJQwt/phFV1freI/ZTug9e2')
ON CONFLICT DO NOTHING;
