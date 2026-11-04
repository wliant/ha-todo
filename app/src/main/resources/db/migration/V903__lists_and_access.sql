-- Lists, and who can see each one (requirements/0004 R18-R20, R27, R28).
--
-- There is no household table and there will not be one: the deployment is the
-- household, so a household_id column would carry one value in every database
-- that will ever exist. Access is a grant per list instead, which is also what
-- lets "Ana's workout" and "Groceries" differ only in audience
-- (docs/adr/0010-per-list-access-instead-of-household-scoping.md).

CREATE TABLE todo_list (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    icon        VARCHAR(40)  NOT NULL,
    -- The field configuration and the access list are both edited from one
    -- settings screen, so two tabs on one list is the contended write here.
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_todo_list_name_not_blank CHECK (btrim(name) <> '')
);

-- Per owner, not globally: two members may each keep a "Groceries", because
-- they are different audiences. One member may not keep two.
CREATE UNIQUE INDEX ux_todo_list_owner_name ON todo_list (owner_id, lower(name));

-- The owner gets a row here too, at creation, so every membership question is
-- one table and one index rather than a column check OR'd with a join.
CREATE TABLE list_access (
    list_id    UUID        NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (list_id, user_id)
);

-- "Which lists can I see" is the question every read starts with.
CREATE INDEX ix_list_access_user ON list_access (user_id);

-- A member's own state for a list. Deliberately not columns on list_access: a
-- grant is a fact someone else asserts about you and carries granted_at, while
-- this is yours and is rewritten whenever you open the list. One row for both
-- would make a recency write contend with a grant being revoked.
--
-- pinned_at IS NULL *is* "not pinned", for the same reason password_hash has no
-- companion boolean: a flag that can disagree with the data eventually does.
CREATE TABLE list_user_state (
    list_id            UUID NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    pinned_at          TIMESTAMPTZ,
    last_interacted_at TIMESTAMPTZ,
    PRIMARY KEY (list_id, user_id)
);

-- The sidebar's two queries, each covered and each per-member.
CREATE INDEX ix_list_user_state_pinned ON list_user_state (user_id, pinned_at DESC)
    WHERE pinned_at IS NOT NULL;
CREATE INDEX ix_list_user_state_recent ON list_user_state (user_id, last_interacted_at DESC)
    WHERE last_interacted_at IS NOT NULL;
