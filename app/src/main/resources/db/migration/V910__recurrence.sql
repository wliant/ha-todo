-- Recurring items (requirements/0005 R41, R42, docs/adr/0025).
--
-- A rule is a template plus a schedule. There is no occurrence queue:
-- last_materialised_on plus the items themselves are the whole state, which is
-- what makes a missed run a no-op rather than a backlog to reconcile.

CREATE TABLE recurrence_rule (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id              UUID         NOT NULL REFERENCES todo_list (id) ON DELETE CASCADE,

    -- The template. Mirrors todo_item's singleton field columns, because a
    -- generated occurrence is an ordinary item and these are its values.
    description          VARCHAR(300) NOT NULL,
    detail_html          TEXT,
    assignee_id          UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    assigned_by          UUID         REFERENCES app_user (id) ON DELETE SET NULL,

    -- The schedule. Three shapes and an interval, which is the whole household
    -- vocabulary: bins (weekly, Tuesday), hoovering (every 2 weeks), rent
    -- (monthly, the 1st), vitamins (daily). Deliberately not RFC 5545: a
    -- parser is a dependency, a UI nobody can drive, and a test matrix for
    -- BYSETPOS with no requirement behind it.
    frequency            VARCHAR(8)   NOT NULL,
    interval_count       SMALLINT     NOT NULL DEFAULT 1,
    -- WEEKLY only. Seven booleans of fixed width, never queried by day, so a
    -- bit mask rather than a child table and three joins to ask "Thursday?".
    -- Monday is bit 0 (value 1) through Sunday bit 6 (value 64).
    days_of_week         SMALLINT,
    -- MONTHLY only. 31 clamps to the last day of a shorter month, which is
    -- what a person means by "the 31st".
    day_of_month         SMALLINT,

    -- A wall clock, not an instant: "the bins go out at 7pm" is 7pm in the
    -- household's zone whatever the server's is. Null means the generated item
    -- carries no due date at all.
    due_time             TIME,
    -- Create the occurrence this many days before it is due, so Sunday's chore
    -- is already on the list on Friday.
    lead_days            SMALLINT     NOT NULL DEFAULT 0,

    starts_on            DATE         NOT NULL,
    ends_on              DATE,
    paused               BOOLEAN      NOT NULL DEFAULT FALSE,

    -- The high-water mark. Generation runs strictly after this, which is what
    -- makes a second run on the same day produce nothing.
    last_materialised_on DATE,
    -- Why a rule produced nothing, kept so the Recurring tab can say so. A
    -- scheduled job in a product with no monitoring fails silently otherwise,
    -- which is the whole risk of R42.
    last_run_at          TIMESTAMPTZ,
    last_error           VARCHAR(200),

    created_by           UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    position             SMALLINT     NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_recurrence_description CHECK (btrim(description) <> ''),
    CONSTRAINT ck_recurrence_frequency
        CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_recurrence_interval CHECK (interval_count BETWEEN 1 AND 52),

    -- Each frequency carries exactly the parameters it uses and none it does
    -- not, the way ck_list_field_label does for cost fields.
    CONSTRAINT ck_recurrence_weekly
        CHECK ((frequency = 'WEEKLY') = (days_of_week IS NOT NULL)),
    CONSTRAINT ck_recurrence_days_of_week
        CHECK (days_of_week IS NULL OR days_of_week BETWEEN 1 AND 127),
    CONSTRAINT ck_recurrence_monthly
        CHECK ((frequency = 'MONTHLY') = (day_of_month IS NOT NULL)),
    CONSTRAINT ck_recurrence_day_of_month
        CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31),

    CONSTRAINT ck_recurrence_window CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_recurrence_lead CHECK (lead_days BETWEEN 0 AND 30)
);

CREATE INDEX ix_recurrence_rule_list ON recurrence_rule (list_id, position);
-- What the job scans: the rules that might owe something.
CREATE INDEX ix_recurrence_rule_due ON recurrence_rule (last_materialised_on)
    WHERE NOT paused;

ALTER TABLE recurrence_rule
    ADD CONSTRAINT ux_recurrence_rule_position UNIQUE (list_id, position)
    DEFERRABLE INITIALLY DEFERRED;

-- A rule's answers to its list's cost fields. Without these, a list with a
-- required cost field could not carry a recurring item at all: generation would
-- fail the same required-field check an item does.
CREATE TABLE recurrence_cost_value (
    rule_id       UUID           NOT NULL REFERENCES recurrence_rule (id) ON DELETE CASCADE,
    list_field_id UUID           NOT NULL REFERENCES list_field (id) ON DELETE CASCADE,
    -- RESTRICT for item_cost_value's reason: deleting a choice a rule has
    -- picked must fail loudly, not quietly empty the rule.
    choice_id     UUID           REFERENCES list_field_choice (id) ON DELETE RESTRICT,
    amount        NUMERIC(12, 4),
    PRIMARY KEY (rule_id, list_field_id),

    CONSTRAINT ck_recurrence_cost_one_of CHECK ((choice_id IS NULL) <> (amount IS NULL)),
    CONSTRAINT ck_recurrence_cost_amount CHECK (amount IS NULL OR amount >= 0)
);

-- Provenance on the generated item, and the thing that actually prevents a
-- double creation. last_materialised_on stops the second run of a day; this
-- stops two runs racing, which is the one way the single-instance assumption in
-- adr/0025 breaks in practice - an ad-hoc run fired while the cron run is in
-- flight.
--
-- ON DELETE SET NULL, and no CHECK pairing the two columns, for the reason
-- ck_todo_item_assignment is absent: the foreign key action would violate it
-- and abort the delete.
ALTER TABLE todo_item ADD COLUMN rule_id UUID REFERENCES recurrence_rule (id) ON DELETE SET NULL;
ALTER TABLE todo_item ADD COLUMN occurrence_on DATE;

CREATE UNIQUE INDEX ux_todo_item_occurrence ON todo_item (rule_id, occurrence_on)
    WHERE rule_id IS NOT NULL;
