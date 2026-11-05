-- Reference collections - recipes, restaurants, quotes, vocabulary - and who
-- can see each one (requirements/0006 R50).
--
-- A sibling of todo_list rather than a "kind" column on it. Nine existing reads
-- of todo_list would otherwise have to remember to exclude a kind they have
-- never heard of - the list index, the sidebar, the dashboard, the field
-- configuration, the recurrence job - and the one that forgets is wrong without
-- failing. Worse, TodoListService.create writes a DESCRIPTION and a DUE_AT
-- field row for every list it creates (docs/adr/0022), so a collection born
-- that way would own a due-date field 0006 says cannot exist.
-- See docs/adr/0029.
CREATE TABLE data_list (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    -- The same curated set todo lists draw from (com.hatodo.list.ListIcon).
    -- No new values: a closed set of drawings with no behaviour, mirrored in
    -- one frontend file.
    icon        VARCHAR(40)  NOT NULL,
    -- The access list is edited from a settings screen, so two tabs open on one
    -- collection is the contended write, exactly as on todo_list.
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_data_list_name_not_blank CHECK (btrim(name) <> '')
);

-- Unique per owner, as on todo_list, and deliberately a *separate* namespace
-- from todo lists: "Restaurants" the collection and "Restaurants" the todo list
-- are different things and one member may want both.
CREATE UNIQUE INDEX ux_data_list_owner_name ON data_list (owner_id, lower(name));

-- A grant is binary. No role column, as in requirements/0004.
CREATE TABLE data_list_access (
    data_list_id UUID        NOT NULL REFERENCES data_list (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by   UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (data_list_id, user_id)
);

-- "Which collections can I see" begins every read, global search included.
CREATE INDEX ix_data_list_access_user ON data_list_access (user_id);
