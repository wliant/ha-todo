-- Demo accounts for local development and the dev environment only; this
-- location is excluded from the prod profile.
--
-- A *repeatable* migration (R__), not a versioned one. Flyway runs repeatable
-- migrations after every versioned migration and re-runs them whenever their
-- content changes, which is exactly right for idempotent seed data: it can
-- never sit in front of a schema change, and editing it is not a checksum
-- violation. The retired versioned seed at V900 is why the schema series now
-- continues from V901 - see that migration's header.
--
-- The two accounts cover both sign-in paths: Ana needs a password
-- ('password123'), Ben needs none. The upsert is what makes that true on a
-- database where V901 carried the old seed rows over - Ben arrived there with a
-- hash and has to lose it.
--
-- Numbered so it runs before the lists seed, which needs these rows to exist:
-- Flyway orders repeatable migrations by description.

INSERT INTO app_user (username, display_name, password_hash, role)
VALUES
    ('ana', 'Ana', '$2y$10$ca5oBvmXYQKrb9NIiHTdZOMdQE75fh/3/6A7RblMXxX9rRB038IYO', 'MEMBER'),
    ('ben', 'Ben', NULL, 'MEMBER')
ON CONFLICT (lower(username)) DO UPDATE
    SET display_name  = EXCLUDED.display_name,
        password_hash = EXCLUDED.password_hash,
        role          = EXCLUDED.role;
