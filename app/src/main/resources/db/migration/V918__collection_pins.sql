-- A member's pins on a collection (0012 R104).
--
-- Its own table rather than a loosened list_user_state.list_id: that column is
-- an FK to todo_list and docs/adr/0029 keeps a data list in its own aggregate,
-- which is exactly why this is more DDL rather than one widened constraint. See
-- docs/adr/0043, which also records that this overrides the "a collection has
-- no pin" consequence stated in adr/0029 and adr/0041.
--
-- pinned_at IS NULL *is* "not pinned", as on list_user_state and for the same
-- reason password_hash has no companion boolean: a flag that can disagree with
-- the data eventually does.
--
-- Deliberately no last_interacted_at twin. A collection's recency comes from
-- data_list_interaction_day (adr/0041), and a second place holding it is a
-- second place to keep true.
CREATE TABLE data_list_user_state (
    data_list_id UUID NOT NULL REFERENCES data_list (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    pinned_at    TIMESTAMPTZ,
    PRIMARY KEY (data_list_id, user_id)
);

-- The pinned section's query, covered and per-member.
CREATE INDEX ix_data_list_user_state_pinned ON data_list_user_state (user_id, pinned_at DESC)
    WHERE pinned_at IS NOT NULL;
