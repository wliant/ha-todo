-- Baseline schema. Only authentication-bearing tables exist at this point; the
-- household / list / item domain arrives on its own feature branch.
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(320) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Login is by email, compared case-insensitively.
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));
