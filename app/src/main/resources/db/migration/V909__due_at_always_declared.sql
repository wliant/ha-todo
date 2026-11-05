-- The due date joins the description as a field every list has
-- (requirements/0005 R40, docs/adr/0022).
--
-- required defaults to false and the owner may still toggle it; only removal is
-- gone. A dashboard that reads across lists can only answer from the lists that
-- declared the field, and "the dashboard silently ignores this list" is not a
-- behaviour anybody would choose.
--
-- Positions are appended per list rather than set to a constant: existing lists
-- already occupy 0..n, and while ux_list_field_position is DEFERRABLE INITIALLY
-- DEFERRED, deferred is not absent - a colliding final state would still fail
-- at commit.
INSERT INTO list_field (list_id, kind, required, position)
SELECT l.id,
       'DUE_AT',
       FALSE,
       (SELECT COALESCE(MAX(f.position), -1) + 1
          FROM list_field f
         WHERE f.list_id = l.id)::smallint
FROM todo_list l
WHERE NOT EXISTS (
    SELECT 1 FROM list_field f
     WHERE f.list_id = l.id AND f.kind = 'DUE_AT'
);
