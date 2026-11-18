-- One number, in one bucket, in one series (requirements/0019 R162, R163).
-- See docs/adr/0052.
CREATE TABLE tracker_reading (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tracker_id  UUID        NOT NULL REFERENCES tracker (id) ON DELETE CASCADE,

    -- Whose series this number belongs to, and NULL when the series is the
    -- household's (a SHARED tracker).
    --
    -- NULL *is* "the household's", with no companion boolean, for the reason
    -- password_hash has none: a flag that can disagree with the data
    -- eventually does. It also makes the fold one grouping with no branch - a
    -- shared tracker simply yields one series.
    --
    -- Which of the two a given tracker's readings must be is a cross-table
    -- invariant SQL cannot state without a trigger, so TrackerReadingService
    -- enforces it - the same place and the same reason ListFieldService
    -- enforces "every list declares a due date" (adr/0022). It fails silently
    -- if broken, so TrackerReadingApiIntegrationTest writes through the API to
    -- check it.
    --
    -- CASCADE: deleting a member takes their own series with them (R172).
    -- SET NULL would silently merge their weights into the household's.
    member_id   UUID        REFERENCES app_user (id) ON DELETE CASCADE,

    -- The first day of the bucket, in app.timezone: the day itself for DAY,
    -- the Monday for WEEK, the first for MONTH. Canonicalised by the service
    -- from whatever the form sent, because the tracker's period lives in
    -- another table and a CHECK cannot read it - and because AT TIME ZONE is
    -- STABLE rather than IMMUTABLE, so Postgres refuses it in a generation
    -- expression anyway (the wall occurs_on hit, adr/0036).
    --
    -- A DATE, never a timestamp. Nothing in this feature puts an instant on
    -- the wire, so there is no second zone for a reader to resolve against.
    period_start DATE       NOT NULL,

    -- Order within a bucket: what LATEST reads, and what an edit keeps.
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 12 digits, 3 decimals: cents, grams and minutes, far beyond anything a
    -- household weighs or spends.
    --
    -- Deliberately no >= 0 check. A weight and a spend are positive, but a
    -- tracker records a change as readily as a level, and constraining the
    -- sign here would be guessing at what a member is counting.
    amount      NUMERIC(12,3) NOT NULL,

    -- Plain text on purpose: no rich text means no sanitizer, no image claim,
    -- and above all no fourth nullable owner column on stored_image - which is
    -- exactly where adr/0032 set the threshold for replacing that shape with a
    -- join table.
    note        VARCHAR(200),

    -- Who typed it. On a PER_MEMBER tracker this equals member_id; on a SHARED
    -- one it is the only record of authorship, which is why it survives the
    -- member's deletion while member_id cascades (R172).
    recorded_by UUID        REFERENCES app_user (id) ON DELETE SET NULL,

    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- There is deliberately NO unique index on (tracker_id, member_id,
-- period_start).
--
-- Several readings in one bucket is the product, not an oversight: three
-- shopping trips make one November, and the tracker's declared rollup is what
-- resolves them (R163). Adding that index later is a product change - it would
-- make the grocery case mean "type the total yourself" - and not a tidy-up.
-- See docs/adr/0052.

-- The series read: every point for one tracker, in bucket order, grouped by
-- series.
CREATE INDEX ix_tracker_reading_series
    ON tracker_reading (tracker_id, period_start, member_id);

-- "Everything this member recorded", which is what deleting a member touches.
CREATE INDEX ix_tracker_reading_member
    ON tracker_reading (member_id, period_start);
