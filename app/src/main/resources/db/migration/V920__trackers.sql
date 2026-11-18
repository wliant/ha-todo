-- Trackers: a number a household records over and over, so it can see the
-- shape it makes (requirements/0019 R159-R161).
--
-- A third aggregate beside todo_list and data_list, not a "kind" on either.
-- adr/0029's argument applies twice over. A tracker is not a todo list with a
-- numeric cost: a cost hangs on a todo_item, which carries an assignee, a due
-- date, a completion and points, and TodoListService.create would hand every
-- tracker a due-date field it can never use (adr/0022). It is not a collection
-- with numeric entries either: data_entry's generated search_vector and its
-- tags exist to find text, and a tracker has no text to find.
-- See docs/adr/0051.
CREATE TABLE tracker (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    -- The same curated set the other two kinds draw from
    -- (com.hatodo.list.ListIcon). A closed set of drawings with no behaviour.
    icon        VARCHAR(40)  NOT NULL,

    -- A label, never a conversion. "kg", "SGD", "minutes". The product does no
    -- arithmetic between units and never will (R159): two units are two
    -- trackers, which is why nothing here holds a conversion factor.
    unit        VARCHAR(16)  NOT NULL,

    -- Which bucket a reading falls in, and therefore which control the form
    -- shows. Fixed once a reading exists - see the service, not a constraint:
    -- SQL cannot see across to tracker_reading without a trigger.
    period      VARCHAR(8)   NOT NULL,

    -- Whose numbers these are (R160). PER_MEMBER: everyone with access keeps
    -- their own series and the chart overlays them. SHARED: one series the
    -- household writes together, stored as a NULL member on each reading
    -- (adr/0052).
    scope       VARCHAR(16)  NOT NULL,

    -- How several readings in one bucket fold into one number (R163), and -
    -- the same rule, the same code - how buckets fold when a long range is
    -- drawn coarsely. One rule, two uses.
    rollup      VARCHAR(8)   NOT NULL,

    -- Display only. The stored amount keeps its full precision; this decides
    -- how many digits the axis and the table print: 72.4 kg, 123.45 SGD,
    -- 30 min.
    decimals    SMALLINT     NOT NULL DEFAULT 1,

    -- An optional goal (R168), drawn on the chart as a reference line.
    target            NUMERIC(12,3),
    target_direction  VARCHAR(8),

    -- The sharing screen is the contended write, exactly as on the other two.
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_tracker_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_tracker_unit_not_blank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_tracker_period   CHECK (period IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT ck_tracker_scope    CHECK (scope  IN ('PER_MEMBER', 'SHARED')),
    CONSTRAINT ck_tracker_rollup   CHECK (rollup IN ('SUM', 'AVERAGE', 'LATEST')),
    CONSTRAINT ck_tracker_decimals CHECK (decimals BETWEEN 0 AND 3),
    -- All or nothing: "70 kg" is not a goal until you know which side of it is
    -- good, so a target without a direction is unreadable rather than merely
    -- incomplete.
    CONSTRAINT ck_tracker_target_pair
        CHECK ((target IS NULL) = (target_direction IS NULL)),
    CONSTRAINT ck_tracker_target_direction
        CHECK (target_direction IS NULL OR target_direction IN ('AT_MOST', 'AT_LEAST'))
);

-- Unique per owner, as on the other two kinds, and a separate namespace from
-- both: "Groceries" the tracker and "Groceries" the list are different things
-- a household may legitimately keep at once.
CREATE UNIQUE INDEX ux_tracker_owner_name ON tracker (owner_id, lower(name));

-- Who can see a tracker, and with it every series on it (R164). A deliberate
-- verbatim copy of data_list_access rather than a shared table: the three
-- features answer the same question about different things, and sharing the
-- implementation is what would couple them (adr/0029).
--
-- The owner holds a row of their own, so membership is one table rather than
-- "the owner, plus these rows".
CREATE TABLE tracker_access (
    tracker_id UUID        NOT NULL REFERENCES tracker (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (tracker_id, user_id)
);

-- "Which trackers can this member see" is the driving predicate of every read.
CREATE INDEX ix_tracker_access_user ON tracker_access (user_id);
