-- Accounts and administration (requirements/0002, adr/0005).
--
-- Numbered 901, not 2: the retired demo seed was a versioned migration at 900,
-- so on any database that applied it a lower version is out of order and Flyway
-- refuses it. The schema series resumes above that mark and the seed is now a
-- repeatable migration (R__), which always runs last and can never block a
-- schema change again.
--
-- Login identity becomes a username rather than an email address, a password
-- becomes optional, and users carry a role so one of them can administer the
-- rest.

-- The index is on lower(email); drop it before the column changes underneath it.
DROP INDEX ux_app_user_email;

ALTER TABLE app_user RENAME COLUMN email TO username;

-- Existing rows hold email addresses. The part before the '@' is what a person
-- would actually type, and is what the rewritten demo seed expects.
UPDATE app_user SET username = split_part(username, '@', 1);

ALTER TABLE app_user ALTER COLUMN username TYPE VARCHAR(50);

CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username));

-- NULL password_hash *is* "no password required"; there is deliberately no
-- companion boolean that could contradict it (adr/0005).
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE app_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'MEMBER'));

-- Exactly one admin is a requirement (R8), so make a second one unrepresentable
-- rather than merely unreachable through the API.
CREATE UNIQUE INDEX ux_app_user_single_admin ON app_user ((role)) WHERE role = 'ADMIN';
