-- Lapsing: a second terminal state for an item (requirements/0015 R131-R140).
--
-- "Planned, and we did not get to it." It earns nothing, it leaves the agenda
-- and the overdue strip, and it can be reopened exactly as a completion can.
-- See docs/adr/0048.

-- Mirrors the reopen pair: the stamp and who made it, moving together.
ALTER TABLE todo_item ADD COLUMN lapsed_at TIMESTAMPTZ;
ALTER TABLE todo_item ADD COLUMN lapsed_by UUID REFERENCES app_user (id) ON DELETE SET NULL;

-- The completion group grows a third member, so the whole group still moves
-- together or not at all. Postgres cannot alter a CHECK, hence drop and
-- recreate - a full-table validation scan, which is nothing at household scale.
--
-- Two clauses are worth reading twice. A lapsed item is worth NOTHING, which is
-- the whole point of having a state that is not "done": points_earned must be
-- null whenever lapsed_at is set. And the reopen clause widens rather than
-- splits - a reopen still implies something to reopen, now either terminal
-- state - while the ordering test splits in two, each half guarded on its own
-- column, because an item can carry both stamps (see below).
ALTER TABLE todo_item DROP CONSTRAINT ck_todo_item_completion;
ALTER TABLE todo_item ADD CONSTRAINT ck_todo_item_completion
    CHECK ((completed_by IS NULL) = (completed_at IS NULL)
           AND (completed_at IS NOT NULL OR points_earned IS NULL)
           AND (completed_at IS NOT NULL OR completion_note_html IS NULL)
           AND (lapsed_at IS NULL) = (lapsed_by IS NULL)
           AND (lapsed_at IS NULL OR points_earned IS NULL)
           AND (reopened_at IS NULL) = (reopened_by IS NULL)
           AND (reopened_at IS NULL OR completed_at IS NOT NULL OR lapsed_at IS NOT NULL)
           AND (reopened_at IS NULL OR completed_at IS NULL OR reopened_at >= completed_at)
           AND (reopened_at IS NULL OR lapsed_at IS NULL OR reopened_at >= lapsed_at));

-- There is deliberately NO constraint making completed_at and lapsed_at
-- mutually exclusive. Complete, reopen, lapse leaves both stamped, and
-- docs/adr/0024 kept the reopened completion precisely so the activity feed
-- does not lose history retroactively. The consequence is carried by
-- TodoItemRepository.pointsByMember, which must therefore test for a *standing*
-- completion rather than reading `closed`.

-- "Finished" was one question and is now two, so the generated column answering
-- "is this item still work?" is renamed as well as widened. `done` would now
-- have to mean "completed or lapsed", and a name that lies is exactly how
-- adr/0024's seventh reader gets written wrong. Renaming breaks every reader at
-- compile time and at schema-validation time, which is the point.
--
-- Nothing in Java writes it: the entity maps it insertable = false,
-- updatable = false, and uses it only in query predicates.
DROP INDEX ix_todo_item_due;
DROP INDEX ix_todo_item_assignee_due;
ALTER TABLE todo_item DROP COLUMN done;
ALTER TABLE todo_item ADD COLUMN closed BOOLEAN NOT NULL
    GENERATED ALWAYS AS ((completed_at IS NOT NULL OR lapsed_at IS NOT NULL)
                         AND reopened_at IS NULL) STORED;

-- Recreated verbatim but for the predicate: a partial index and the query that
-- hopes to use it have to name the same column, or the planner ignores it.
CREATE INDEX ix_todo_item_due ON todo_item (list_id, due_at)
    WHERE closed = FALSE;
CREATE INDEX ix_todo_item_assignee_due ON todo_item (assignee_id, due_at)
    WHERE closed = FALSE AND assignee_id IS NOT NULL;

-- ix_todo_item_activity is partial on completed_at and is deliberately left
-- alone: the activity feed is about completions, and a lapse is not one.
