-- A member's pin on a tracker, and how often they open each one
-- (requirements/0019 R170).
--
-- Two more tables rather than widened ones, for the reason V918 gives: the
-- existing state and bucket tables carry FKs to todo_list and data_list, and
-- adr/0029 keeps each kind in its own aggregate. More DDL is the price of that
-- and it is the price already paid twice.
CREATE TABLE tracker_user_state (
    tracker_id UUID NOT NULL REFERENCES tracker (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- pinned_at IS NULL *is* "not pinned", as on the other two.
    pinned_at  TIMESTAMPTZ,
    PRIMARY KEY (tracker_id, user_id)
);

-- The pinned section's query, covered and per-member.
CREATE INDEX ix_tracker_user_state_pinned ON tracker_user_state (user_id, pinned_at DESC)
    WHERE pinned_at IS NOT NULL;

-- One row per (tracker, member, household day), pruned on a rolling window
-- (adr/0041). A monotonic counter would rank lifetime use and never forget,
-- which is the opposite of what the sidebar's ordering is for.
--
-- on_day is application-supplied in app.timezone, for the reason period_start
-- is: AT TIME ZONE is STABLE, not IMMUTABLE.
--
-- A pin must never be stored here: these rows are pruned on a rolling thirty
-- days, so a pin kept on one would silently expire.
CREATE TABLE tracker_interaction_day (
    tracker_id UUID        NOT NULL REFERENCES tracker (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    on_day     DATE        NOT NULL,
    hits       INTEGER     NOT NULL DEFAULT 1,
    last_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tracker_id, user_id, on_day),
    CONSTRAINT ck_tracker_interaction_day_hits CHECK (hits > 0)
);

-- The frequency sum and the prune both read (member, day).
CREATE INDEX ix_tracker_interaction_day_window ON tracker_interaction_day (user_id, on_day);
