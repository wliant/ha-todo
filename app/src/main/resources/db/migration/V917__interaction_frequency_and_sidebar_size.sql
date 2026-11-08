-- How often a member uses each list, over a rolling month (requirements/0012
-- R100), plus how many of them the sidebar shows (R102).
--
-- One row per (list, member, household day) carrying a count, rather than one
-- row per visit or one monotonic counter. See docs/adr/0041: a counter that
-- only goes up ranks lifetime use and never forgets, and per-visit rows grow
-- without bound unless something prunes them - and this product deliberately
-- has exactly one scheduled job (docs/adr/0025).
--
-- Three things fall out of the daily bucket, and all three are why it is the
-- shape: the fifteen-minute dedup window can be read from the row being
-- written, so this needs no companion state table and collections can have it
-- too; each write prunes the member's expired buckets, so the rows read are the
-- rows kept; and the table is bounded by construction at one row per list per
-- member per day.
--
-- on_day is supplied by the application in app.timezone, not generated, for the
-- reason occurs_on is (docs/adr/0036): AT TIME ZONE is STABLE rather than
-- IMMUTABLE and Postgres refuses it in a generation expression.
CREATE TABLE list_interaction_day (
    list_id UUID        NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,
    user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    on_day  DATE        NOT NULL,
    hits    INTEGER     NOT NULL DEFAULT 1,
    -- When this bucket was last written. The dedup window is read from here, so
    -- nothing outside this table decides whether a visit counts.
    last_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (list_id, user_id, on_day),
    CONSTRAINT ck_list_interaction_day_hits CHECK (hits > 0)
);

-- Both the window read and the prune start from the member, never from the
-- list: the prune is scoped to user_id, because scoping it to a list would
-- leave stale buckets for exactly the lists the member stopped opening.
CREATE INDEX ix_list_interaction_day_window ON list_interaction_day (user_id, on_day);

-- The same shape for collections, which have no per-member state at all
-- (docs/adr/0029 keeps list_user_state.list_id pointed at todo_list). Two
-- tables rather than one with a nullable pair of owner keys: the duplication is
-- a DDL block, and the separation is the point.
CREATE TABLE data_list_interaction_day (
    data_list_id UUID        NOT NULL REFERENCES data_list (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    on_day       DATE        NOT NULL,
    hits         INTEGER     NOT NULL DEFAULT 1,
    last_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (data_list_id, user_id, on_day),
    CONSTRAINT ck_data_list_interaction_day_hits CHECK (hits > 0)
);

CREATE INDEX ix_data_list_interaction_day_window
    ON data_list_interaction_day (user_id, on_day);

-- R102. Personal rather than per-device, so it sits beside the theme on the
-- row that already holds a member's preferences.
ALTER TABLE app_user
    ADD COLUMN sidebar_list_count INTEGER NOT NULL DEFAULT 10;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_sidebar_list_count
        CHECK (sidebar_list_count BETWEEN 1 AND 30);
