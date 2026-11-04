-- What shape a list's items take (requirements/0004 R21-R23).
--
-- Typed rows rather than a JSONB blob on todo_list: the kinds are a closed set
-- that only this codebase can add to, item values will reference a field by
-- foreign key, and every rule below is a constraint the database holds rather
-- than application code a bad migration can walk past. See
-- docs/adr/0011-item-fields-as-a-typed-per-list-declaration.md.

CREATE TABLE list_field (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id         UUID         NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,
    kind            VARCHAR(16)  NOT NULL,
    -- Cost fields are named by the creator ("Time", "Money"); the singletons
    -- are not, because there is only ever one of each.
    label           VARCHAR(30),
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    position        SMALLINT     NOT NULL,
    cost_mode       VARCHAR(8),
    cost_unit_label VARCHAR(20),
    points_per_unit NUMERIC(12, 4),

    CONSTRAINT ck_list_field_kind
        CHECK (kind IN ('DESCRIPTION', 'DETAIL', 'ASSIGNEE', 'DUE_AT', 'COST')),

    -- Description is always present and always required (R21). Making the
    -- unrequired variant unrepresentable beats validating against it.
    CONSTRAINT ck_list_field_description_required
        CHECK (kind <> 'DESCRIPTION' OR required),

    CONSTRAINT ck_list_field_label CHECK ((kind = 'COST') = (label IS NOT NULL)),
    CONSTRAINT ck_list_field_cost_only CHECK ((kind = 'COST') = (cost_mode IS NOT NULL)),
    CONSTRAINT ck_list_field_cost_mode
        CHECK (cost_mode IS NULL OR cost_mode IN ('FIXED', 'NUMERIC')),

    -- A numeric cost is measured in something; fixed choices carry their own
    -- labels and their own points, so they need neither unit nor rate.
    CONSTRAINT ck_list_field_numeric_unit
        CHECK (cost_mode IS DISTINCT FROM 'NUMERIC' OR cost_unit_label IS NOT NULL),
    CONSTRAINT ck_list_field_fixed_has_no_unit
        CHECK (cost_mode IS DISTINCT FROM 'FIXED'
               OR (cost_unit_label IS NULL AND points_per_unit IS NULL)),

    CONSTRAINT ck_list_field_points_non_negative
        CHECK (points_per_unit IS NULL OR points_per_unit >= 0)
);

-- The non-cost kinds are singletons. Cost fields are many, told apart by name.
CREATE UNIQUE INDEX ux_list_field_singleton
    ON list_field (list_id, kind) WHERE kind <> 'COST';
CREATE UNIQUE INDEX ux_list_field_cost_label
    ON list_field (list_id, lower(label)) WHERE kind = 'COST';

-- Deferred on purpose. Reordering fields rewrites several positions in one
-- statement, and a non-deferred constraint fires part-way through on a
-- perfectly valid final state. A partial unique *index* cannot be deferred,
-- which is why this one is a table constraint instead.
ALTER TABLE list_field
    ADD CONSTRAINT ux_list_field_position UNIQUE (list_id, position)
    DEFERRABLE INITIALLY DEFERRED;

-- The selectable costs of a FIXED field (R22, R23). A choice is a label worth
-- points and carries no numeric amount, so a list totals points rather than
-- hours - the trade is recorded in adr/0012.
CREATE TABLE list_field_choice (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    list_field_id UUID        NOT NULL REFERENCES list_field (id) ON DELETE CASCADE,
    label         VARCHAR(30) NOT NULL,
    points        INTEGER     NOT NULL DEFAULT 0,
    position      SMALLINT    NOT NULL,
    CONSTRAINT ck_list_field_choice_points CHECK (points >= 0),
    CONSTRAINT ck_list_field_choice_label CHECK (btrim(label) <> '')
);

CREATE UNIQUE INDEX ux_list_field_choice_label
    ON list_field_choice (list_field_id, lower(label));

ALTER TABLE list_field_choice
    ADD CONSTRAINT ux_list_field_choice_position UNIQUE (list_field_id, position)
    DEFERRABLE INITIALLY DEFERRED;

-- Every list that already exists gets the one field every list always has.
INSERT INTO list_field (list_id, kind, required, position)
SELECT l.id, 'DESCRIPTION', TRUE, 0
FROM todo_list l
WHERE NOT EXISTS (
    SELECT 1 FROM list_field f WHERE f.list_id = l.id AND f.kind = 'DESCRIPTION'
);
