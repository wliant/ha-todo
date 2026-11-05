-- Completion detail, self-claimed assignment, and reopening that remembers
-- (requirements/0005 R36, R37, R47, R49).

-- The note a member writes about how the work went. Sanitized server-side on
-- write like detail_html is, and under the same policy (docs/adr/0018). It is a
-- column rather than a list_field kind because it is a property of the act of
-- completing, which every list does identically, rather than of an item's
-- shape - see docs/adr/0023.
ALTER TABLE todo_item ADD COLUMN completion_note_html TEXT;

-- Who put the assignee there. assignee_id = assigned_by is "they claimed it
-- themselves", which is the only question this column answers (R47).
--
-- Deliberately NOT paired with assignee_id by a CHECK. Both columns are
-- ON DELETE SET NULL, and Postgres applies a foreign key action as an update
-- that a cross-column CHECK would then reject - aborting the deletion of
-- exactly those accounts that had been used. The self-claim test is already
-- false on a null assignee, so the constraint would buy nothing. See
-- docs/adr/0026.
ALTER TABLE todo_item ADD COLUMN assigned_by UUID REFERENCES app_user (id) ON DELETE SET NULL;

-- Reopening stamps rather than erases: R49 keeps what the completion recorded
-- and only takes the points back.
ALTER TABLE todo_item ADD COLUMN reopened_at TIMESTAMPTZ;
ALTER TABLE todo_item ADD COLUMN reopened_by UUID REFERENCES app_user (id) ON DELETE SET NULL;

-- The completion columns still move together, and the note is now one of them:
-- a note on an item nobody completed would be a sentence about work that was
-- never done. A reopen stamp implies a completion to reopen, and comes no
-- earlier than it.
--
-- Postgres cannot alter a CHECK, hence drop and recreate. It is a full-table
-- validation scan, which is nothing at household scale.
ALTER TABLE todo_item DROP CONSTRAINT ck_todo_item_completion;
ALTER TABLE todo_item ADD CONSTRAINT ck_todo_item_completion
    CHECK ((completed_by IS NULL) = (completed_at IS NULL)
           AND (completed_at IS NOT NULL OR points_earned IS NULL)
           AND (completed_at IS NOT NULL OR completion_note_html IS NULL)
           AND (reopened_at IS NULL) = (reopened_by IS NULL)
           AND (reopened_at IS NULL OR completed_at IS NOT NULL)
           AND (reopened_at IS NULL OR reopened_at >= completed_at));

-- Because the completion record survives a reopen, "completed_at IS NOT NULL"
-- stops meaning finished. The definition lives here, computed by the database,
-- because that is the one version that provably cannot drift from the columns
-- it is derived from - and this codebase refuses maintained booleans for good
-- reason (docs/adr/0005, and docs/adr/0024 for why this is the exception).
--
-- Nothing in Java writes it: the entity maps it insertable = false,
-- updatable = false, and uses it only in query predicates.
ALTER TABLE todo_item ADD COLUMN done BOOLEAN NOT NULL
    GENERATED ALWAYS AS (completed_at IS NOT NULL AND reopened_at IS NULL) STORED;

-- The dashboard's three ranges and its no-due-date panel, from one index: a
-- btree on (list_id, due_at) range-scans a window and finds the nulls, which
-- sort last and are indexable. Reached through list_access (R44, R45).
CREATE INDEX ix_todo_item_due ON todo_item (list_id, due_at)
    WHERE done = FALSE;

-- The dashboard's scope=mine, which does not start from a list.
CREATE INDEX ix_todo_item_assignee_due ON todo_item (assignee_id, due_at)
    WHERE done = FALSE AND assignee_id IS NOT NULL;

-- Activity over a window on one list (R43). ix_todo_item_completer is
-- (list_id, completed_by) and cannot answer a time range.
CREATE INDEX ix_todo_item_activity ON todo_item (list_id, completed_at DESC)
    WHERE completed_at IS NOT NULL;
