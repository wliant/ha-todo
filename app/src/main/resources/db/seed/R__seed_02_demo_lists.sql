-- Demo lists for local and dev (this location is not on prod's classpath).
--
-- Repeatable, like the user seed, so it always runs after every versioned
-- migration and can be edited freely. It must therefore be idempotent on its
-- own terms: Flyway re-runs a repeatable migration whenever its checksum
-- changes, not only on a fresh database.
--
-- The numeric prefix is load-bearing. Flyway orders repeatable migrations by
-- description, so the unprefixed name sorted *before* the users seed and, on a
-- fresh database, found nobody to own these lists - inserting nothing, without
-- failing. The prefix makes the dependency on the users seed explicit and
-- correct.
--
-- The point of the second list is that it is granted to Ana alone. Signing in
-- as Ben and not seeing it is the access rule (R19) being visible from the
-- first minute, rather than something only a test knows about.

INSERT INTO todo_list (owner_id, name, description, icon)
SELECT u.id, 'Groceries (demo)', 'What we need from the shop', 'GROCERIES'
FROM app_user u
WHERE u.username = 'ana'
  AND NOT EXISTS (
      SELECT 1 FROM todo_list l WHERE l.owner_id = u.id AND l.name = 'Groceries (demo)'
  );

INSERT INTO todo_list (owner_id, name, description, icon)
SELECT u.id, 'Ana''s workout (demo)', 'Private to Ana', 'FITNESS'
FROM app_user u
WHERE u.username = 'ana'
  AND NOT EXISTS (
      SELECT 1 FROM todo_list l WHERE l.owner_id = u.id AND l.name = 'Ana''s workout (demo)'
  );

-- The owner always holds a grant of their own, so membership is one table.
INSERT INTO list_access (list_id, user_id, granted_by)
SELECT l.id, l.owner_id, l.owner_id
FROM todo_list l
WHERE l.name IN ('Groceries (demo)', 'Ana''s workout (demo)')
  AND NOT EXISTS (
      SELECT 1 FROM list_access a WHERE a.list_id = l.id AND a.user_id = l.owner_id
  );

-- Ben can see the shopping list, and only that one.
INSERT INTO list_access (list_id, user_id, granted_by)
SELECT l.id, ben.id, l.owner_id
FROM todo_list l
JOIN app_user ben ON ben.username = 'ben'
WHERE l.name = 'Groceries (demo)'
  AND NOT EXISTS (
      SELECT 1 FROM list_access a WHERE a.list_id = l.id AND a.user_id = ben.id
  );

-- Both fields every list declares (R21, R40). The seed inserts rows directly
-- rather than going through TodoListService, which is where that guarantee
-- normally lives - so without this the demo lists are the only lists in the
-- product with no description field, and they stopped being representative the
-- moment the due date joined it (docs/adr/0022).
--
-- On a fresh database this is the only thing that gives these lists either
-- field: repeatable migrations run after every versioned one, so V909's
-- backfill has no lists to find yet. On a database that already had them,
-- V909 has already added the due date, and the appended position is what keeps
-- these inserts off it - ux_list_field_position is deferred, not absent.
INSERT INTO list_field (list_id, kind, required, position)
SELECT l.id, 'DESCRIPTION', TRUE,
       (SELECT COALESCE(MAX(f.position), -1) + 1
          FROM list_field f WHERE f.list_id = l.id)::smallint
FROM todo_list l
WHERE l.name IN ('Groceries (demo)', 'Ana''s workout (demo)')
  AND NOT EXISTS (
      SELECT 1 FROM list_field f WHERE f.list_id = l.id AND f.kind = 'DESCRIPTION'
  );

INSERT INTO list_field (list_id, kind, required, position)
SELECT l.id, 'DUE_AT', FALSE,
       (SELECT COALESCE(MAX(f.position), -1) + 1
          FROM list_field f WHERE f.list_id = l.id)::smallint
FROM todo_list l
WHERE l.name IN ('Groceries (demo)', 'Ana''s workout (demo)')
  AND NOT EXISTS (
      SELECT 1 FROM list_field f WHERE f.list_id = l.id AND f.kind = 'DUE_AT'
  );
