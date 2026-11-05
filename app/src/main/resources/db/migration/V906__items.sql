-- Items (requirements/0004 R29, R30, and R23's points).
--
-- The singleton field values are columns here; only cost repeats per list, so
-- only cost values are rows. That keeps assignee a real foreign key and due_at
-- a real timestamp, rather than everything becoming strings in an EAV table
-- (docs/adr/0011).

CREATE TABLE todo_item (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id       UUID         NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,

    -- The field every list has, so a column rather than configuration.
    description   VARCHAR(300) NOT NULL,
    -- Sanitized server-side on every write (docs/adr/0018). Null when the list
    -- declares no detail field, or declares one and it is empty.
    detail_html   TEXT,
    assignee_id   UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    due_at        TIMESTAMPTZ,

    completed_by  UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    completed_at  TIMESTAMPTZ,
    -- Snapshotted at completion from the configuration as it stood then, and
    -- never recomputed: editing a rate afterwards must not silently rewrite
    -- somebody's score (docs/adr/0012).
    points_earned INTEGER,

    created_by    UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    position      INTEGER      NOT NULL,
    -- Completion and reassignment are the contended writes this product was
    -- always going to have. A second writer is told, not silently overwritten.
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_todo_item_description CHECK (btrim(description) <> ''),

    -- The three completion columns move together or not at all.
    CONSTRAINT ck_todo_item_completion
        CHECK ((completed_by IS NULL) = (completed_at IS NULL)
               AND (completed_at IS NOT NULL OR points_earned IS NULL)),
    CONSTRAINT ck_todo_item_points CHECK (points_earned IS NULL OR points_earned >= 0)
);

CREATE INDEX ix_todo_item_list ON todo_item (list_id, position);
-- Drives the per-list leaderboard (R23) without a counter to drift from it.
CREATE INDEX ix_todo_item_completer ON todo_item (list_id, completed_by)
    WHERE completed_by IS NOT NULL;

CREATE TABLE item_cost_value (
    item_id       UUID          NOT NULL REFERENCES todo_item (id) ON DELETE CASCADE,
    list_field_id UUID          NOT NULL REFERENCES list_field (id) ON DELETE CASCADE,
    -- Set for a FIXED cost, null for a NUMERIC one. RESTRICT rather than SET
    -- NULL: deleting a choice items have already picked must fail loudly, not
    -- quietly empty their cost.
    choice_id     UUID          REFERENCES list_field_choice (id) ON DELETE RESTRICT,
    amount        NUMERIC(12, 4),
    PRIMARY KEY (item_id, list_field_id),

    -- A cost value is a chosen label or a measured amount. Never both, never
    -- neither.
    CONSTRAINT ck_item_cost_value_one_of CHECK ((choice_id IS NULL) <> (amount IS NULL)),
    CONSTRAINT ck_item_cost_value_amount CHECK (amount IS NULL OR amount >= 0)
);
