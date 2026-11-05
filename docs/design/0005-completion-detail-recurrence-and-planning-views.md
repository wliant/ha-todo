# 0005 - Completion detail, recurrence, and planning views

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md) R36-R49
Builds on: [design/0004](0004-lists-items-and-personal-settings.md)
Amends: [design/0004](0004-lists-items-and-personal-settings.md) - the due date is
no longer an opt-in field, the item row's checkbox no longer completes, and
`PointsPanel` moves onto a tab. [design/0003](0003-pwa-and-ui-shell.md) - the
landing page is the dashboard, not the list of lists.

## Scope

Four surfaces and one piece of machinery. The item row gains a completion note and
an open/closed state (R36-R38); the add form gains a collapsed mode (R39); the list
gains two more tabs - standing instructions and this week's activity (R41-R43); a
dashboard reads across lists (R44, R45); and the row's controls are reworked so
selecting, claiming, completing and reopening are four different things (R46-R49).
The machinery is a scheduled job, the first thing in this product that acts without
being asked (R42).

Out of scope: everything in requirements/0005's "Explicitly not required", and
notably notifications, catching up on missed occurrences, and any recurrence
grammar richer than daily / chosen weekdays / a day of the month.

Decisions behind this design:
[adr/0022](../adr/0022-the-due-date-is-declared-on-every-list.md) (every list
declares a due date, and cannot drop it),
[adr/0023](../adr/0023-the-completion-note-is-part-of-the-completion.md) (a second
rich-text column, not a field kind),
[adr/0024](../adr/0024-reopening-keeps-the-record-and-done-becomes-derived.md) (the
reopen stamp, and a generated `done` column),
[adr/0025](../adr/0025-recurrence-is-materialised-by-a-scheduled-job-on-one-instance.md)
(a scheduler, one instance, and the unique index that makes it safe),
[adr/0026](../adr/0026-claiming-is-recorded-not-inferred.md) (`assigned_by`, and
partial success for a bulk assign),
[adr/0027](../adr/0027-complete-is-a-button-and-the-checkbox-selects.md) (the row's
controls, the hold accelerator, and its keyboard alternative),
[adr/0028](../adr/0028-the-dashboard-reads-across-lists-and-is-the-landing-page.md)
(one cross-list read path, and the landing page moves).

## Data

New and changed columns only; everything else is as design/0004 left it.

```
todo_item                                    -- existing table, six new columns
  completion_note_html  text                 -- R37; sanitized on write, adr/0018
  assigned_by           uuid -> app_user     -- R47; who put the assignee there
  reopened_at           timestamptz          -- R49; set, not cleared, on reopen
  reopened_by           uuid -> app_user     -- R49
  done                  boolean generated    -- completed_at IS NOT NULL
                                             --   AND reopened_at IS NULL (adr/0024)
  rule_id               uuid -> recurrence_rule  -- R42; provenance, nullable
  occurrence_on         date                 -- R42; which occurrence this is

recurrence_rule                              -- R41
  id                 uuid pk
  list_id            uuid not null -> todo_list    -- cascade; the list is the authority
  description        varchar(300) not null         -- the template, mirroring
  detail_html        text                          --   todo_item's singleton columns
  assignee_id        uuid -> app_user              --   because an occurrence is an
  assigned_by        uuid -> app_user              --   ordinary item (R41)
  frequency          varchar(8) not null           -- DAILY | WEEKLY | MONTHLY
  interval_count     smallint not null             -- "every other", 1..52
  days_of_week       smallint                      -- WEEKLY only; 7-bit mask, Mon=1
  day_of_month       smallint                      -- MONTHLY only; 1..31, clamped
  due_time           time                          -- a wall clock, not an instant
  lead_days          smallint not null             -- create it this early
  starts_on          date not null
  ends_on            date
  paused             boolean not null
  last_materialised_on date                        -- the high-water mark
  last_run_at        timestamptz                   -- so the tab can say what happened
  last_error         varchar(200)                  --   when a rule produces nothing
  created_by         uuid -> app_user
  position           smallint not null             -- unique per list, deferrable
  version            bigint not null               -- @Version; owners contend
  created_at / updated_at

recurrence_cost_value                        -- a rule's answers to its list's costs
  rule_id        uuid not null -> recurrence_rule
  list_field_id  uuid not null -> list_field
  choice_id      uuid -> list_field_choice   -- restrict, as item_cost_value does
  amount         numeric(12,4)
                                             -- pk (rule_id, list_field_id)

stored_image                                 -- existing table, one new column
  rule_id        uuid -> recurrence_rule      -- R41; a rule owns its pictures
```

### Invariants, and who holds each

| Invariant | Held by |
|---|---|
| The completion columns move together, and the note is one of them | `ck_todo_item_completion`, extended with `completed_at IS NOT NULL OR completion_note_html IS NULL` |
| A reopen stamp implies a completion to reopen, and comes after it | `ck_todo_item_completion`, `reopened_at >= completed_at` |
| "Done" cannot disagree with the columns it is derived from | `done`, a **stored generated column**; nothing in Java writes it |
| An occurrence is created once per rule per date | `ux_todo_item_occurrence (rule_id, occurrence_on) WHERE rule_id IS NOT NULL` |
| A frequency carries exactly the parameters it uses | `ck_recurrence_weekly`, `ck_recurrence_monthly` - each an `=` between a kind test and a null test, as `ck_list_field_label` does for cost fields |
| A rule's window is not inside out | `ck_recurrence_window` |
| A picture belongs to an item or to a rule, never both | `ck_stored_image_owner` |
| Every list declares a due date | `ListFieldService.normalise`, not SQL - see adr/0022 |
| An assignee can see the list | `TodoItemService`, as R24 already required |

**Two constraints are deliberately absent**, and the reason is the same for both. A
`CHECK (assignee_id IS NOT NULL OR assigned_by IS NULL)` looks obviously right and
would turn deleting an account into a 500: `assignee_id` is `ON DELETE SET NULL`
and `assigned_by` would be too, but Postgres applies the FK actions as an update
that the CHECK then rejects, aborting the delete of exactly those accounts that
were used. Same for `rule_id` / `occurrence_on`. **Never write a CHECK spanning a
column with an `ON DELETE SET NULL` action and one without.** Neither constraint
buys anything: the self-claim test is `assignee_id IS NOT NULL AND assignee_id =
assigned_by`, already false on a null assignee.

### The reopen stamp, and what "done" means now

R49 keeps the completion record through a reopen, so `completedAt != null` stops
meaning "finished". Six places tested completion that way and must now agree on one
definition:

1. `TodoItem.isCompleted()`
2. `TodoItemRepository.findForList`'s `status` predicate
3. `ItemExceptionHandler.describe(ItemResponse)`, which names the other member on a 409
4. `ItemRow`'s strike-through and completed chip
5. every partial index whose predicate was `WHERE completed_at IS NULL`
6. the dashboard and activity queries

The definition lives in the database as a stored generated column, because that is
the one version that provably cannot drift:

```sql
ALTER TABLE todo_item ADD COLUMN done BOOLEAN NOT NULL
    GENERATED ALWAYS AS (completed_at IS NOT NULL AND reopened_at IS NULL) STORED;
```

Mapped `@Column(name = "done", insertable = false, updatable = false)` and used
**only** in JPQL predicates and index definitions. `isCompleted()` stays computed in
Java: Hibernate does not re-select a generated column after a write, so a Java
reader of the mapped field sees a stale value immediately after `completeBy` - which
would render a just-completed item as open. That hazard needs a comment on the field
or the next reader will use it in `ItemResponse.of`.

Reopening nulls `points_earned` only (R49 returns the points), and
`TodoItemRepository.pointsByMember` already sums a nullable column, so the
leaderboard needs no change.

### Migration path

| Migration | Phase | Contents |
|---|---|---|
| `V908__completion_detail_and_reopening.sql` | A | `completion_note_html`, `assigned_by`, `reopened_at`, `reopened_by`, the generated `done`; `ck_todo_item_completion` dropped and recreated; `ix_todo_item_due`, `ix_todo_item_assignee_due`, `ix_todo_item_activity` |
| `V909__due_at_always_declared.sql` | A | a `DUE_AT` `list_field` row for every list that lacks one, `required = false` |
| `V910__recurrence.sql` | D | `recurrence_rule`, `recurrence_cost_value`, `todo_item.rule_id`, `occurrence_on`, `ux_todo_item_occurrence` |
| `V911__rule_images.sql` | D | `stored_image.rule_id`, `ix_stored_image_rule`, `ck_stored_image_owner` |

Postgres cannot alter a CHECK, hence drop-and-recreate in V908; it is a full-table
validation scan and trivial at household scale.

V909's positions are **appended per list**, never set to a constant:

```sql
INSERT INTO list_field (list_id, kind, required, position)
SELECT l.id, 'DUE_AT', FALSE,
       (SELECT COALESCE(MAX(f.position), -1) + 1
          FROM list_field f WHERE f.list_id = l.id)::smallint
FROM todo_list l
WHERE NOT EXISTS (SELECT 1 FROM list_field f
                   WHERE f.list_id = l.id AND f.kind = 'DUE_AT');
```

`ux_list_field_position` is `DEFERRABLE INITIALLY DEFERRED`, so the per-list
`MAX(position) + 1` subselect commits cleanly - deferred, not absent, so a
colliding *final* state would still fail. `WHERE NOT EXISTS` leaves a list that
already declared `DUE_AT` exactly as it is, `required = true` included.

## Backend

New and changed endpoints only.

```
PUT    /api/items/{id}/completion         access    CompletionRequest gains noteHtml (R37, R48)
POST   /api/lists/{id}/items/assignment   access    bulk assign, partial success (R46)

GET    /api/lists/{id}/recurrence         access    List<RecurrenceRuleResponse>   (R41)
POST   /api/lists/{id}/recurrence         access    201; SaveRecurrenceRuleRequest
PATCH  /api/recurrence/{ruleId}           managed   SaveRecurrenceRuleRequest
DELETE /api/recurrence/{ruleId}?version=  managed   204
POST   /api/lists/{id}/recurrence/runs    access    { generated, skipped }          (R42)
POST   /api/admin/recurrence/runs         ADMIN     { rulesScanned, generated, skipped, failed }

GET    /api/lists/{id}/activity?from=&to= access    members[] + events[]            (R43)

GET    /api/dashboard/scheduled?from=&to=&scope=    { from, to, items[] }           (R44)
GET    /api/dashboard/undated?scope=                { items[], total }              (R45)
```

`CompletionRequest` becomes `(Long version, Boolean completed, String noteHtml)`.
The note rides the completion rather than taking an endpoint of its own: it is only
meaningful as part of becoming complete, a separate write would leave a window in
which the item is done with no note, and the hold gesture produces one intent so it
should produce one request. Reopening *with* a note is a **400**, not a silent drop
- the CHECK would otherwise make it a 500, and a quietly discarded field hides a
client bug for a release.

`ItemResponse` gains `completionNoteHtml`, `selfAssigned` and `reopenedAt`.
`selfAssigned` is computed server-side as `assignee != null && assignee.id ==
assignedBy`; `assignedBy` itself is **not** published as a member, because the badge
needs a boolean and the id would publish "Ana assigned this to Ben" as a separate
fact nothing asks for.

### Authorization

Unchanged in shape: every new read and write goes through `TodoListService`'s
`requireReadable` / `requireManagedAndFresh`, no access answers **404**, and list
queries carry the `EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = ... AND
a.user.id = :userId)` guard in SQL. Three new notes:

- **Rules are managed, items are shared.** Writing a rule needs ownership (R20's
  reading: the shape of the list is the owner's); running one needs only access,
  because a member who just saw a rule wants to see it work and the result is an
  item they could have added by hand.
- **The admin's ad-hoc run returns counts and nothing else.** It creates items on
  lists the admin cannot see, which sits against adr/0010, and is defensible only
  because it reads nothing back: no description, no list name, no rule id. Resist
  the obvious next request for a per-rule breakdown.
- **The dashboard is the caller's grants, not the caller's assignments.** See
  "Reading across lists".

### The recurrence engine

`com.hatodo.recurrence`: `RecurrenceRule`, `RecurrenceCostValue`,
`RecurrenceRuleRepository`, `RecurrenceRuleService` (CRUD and authorization),
`RecurrenceCalculator` (pure - `List<LocalDate> occurrences(rule, from, to)`, no
Spring, no clock, so the arithmetic is unit-testable), `RecurrenceMaterialiser`
(`@Transactional`, **one transaction per rule**), `RecurrenceRunner`
(`@Scheduled`, untransacted, loops and catches per rule), `RecurrenceController`.

**Only ids cross from the runner into the materialiser.** The runner is
untransacted precisely so one rule's failure cannot roll back another's, which
means every entity it loads is detached - and the first touch of a lazy
collection inside the materialiser throws. So `materialise(UUID ruleId)` loads
its own rule. Found the hard way on the first local run, where every rule
reported `failed`.

`TodoItem.ruleId` is a plain `UUID` column, **not** a `@ManyToOne` - the precedent
`StoredImage.itemId` sets - so `item` does not depend on `recurrence`. The
dependency runs one way.

**Expressiveness** is three frequencies with an interval, and that is the whole
household vocabulary: bins (weekly, Tuesday), hoovering (every 2 weeks), rent
(monthly, the 1st), vitamins (daily). `days_of_week` is a 7-bit mask rather than a
child table, because it is a fixed-width set of seven booleans that is never queried
by day; the bit mapping lives in one constant with one test. `day_of_month = 31`
**clamps to the last day of the month** - which is what a person means by "the
31st" in February - and that is documented here because "skip the month" is the
other defensible reading and silently picking one is how a missed rent reminder
happens.

**Double creation is prevented twice**, mirroring design/0004's stance on `@Version`:

1. `last_materialised_on` is the deterministic guard. A run generates over
   `[max(last_materialised_on + 1 day, today - catchUpDays), today + lead_days]`
   and advances the mark in the same transaction, so a second run the same day
   computes an empty range.
2. The occurrence is **asked for before it is inserted**, and
   `ux_todo_item_occurrence` is the backstop behind that. An existing occurrence
   is counted `skipped`. **The failure mode is a log line, not a duplicate
   chore.**

   Corrected during implementation: this originally said the materialiser
   *catches* the constraint violation per occurrence, which cannot work.
   Hibernate defers the insert to flush, so the violation arrives at commit -
   after the loop has finished, attributable to no particular occurrence, and
   with the session already poisoned. Hence the existence check. The index still
   guards two runs genuinely racing, and that case fails the rule's whole
   transaction, which the runner records against the rule.

**Downtime**: `app.recurrence.catch-up-days` defaults to **0**. A chore due last
Tuesday is not created on Friday; it is noise a member has to delete. A property
rather than a constant so a household that disagrees needs no code change.

**An occurrence inherits** description, `detail_html` (already sanitized at rule-save
time, so no second pass), `assignee_id`, `assigned_by`, the rule's cost values
copied into `item_cost_value`, `created_by` = the rule's author (a real person; a
synthetic "system" user would be a lie in the audit trail), `due_at` =
`occurrence_on` at `due_time` in the household zone, `position` = next on the list,
`rule_id` and `occurrence_on`. It **never** inherits `points_earned` (the CHECK
forbids it on an open item, and adr/0012 computes it at completion), the completion
columns, the note, or a version.

**A list's deletion must not load its rules.** The pictures behind a rule's
detail are already let go by `StoredImageRepository.findForList`, which is a
native query spanning rule-owned rows. Loading `RecurrenceRule` *entities* while
the list is being deleted is worse than redundant: the `TodoList` they point at
has already left the session, so the flush fails its transient-reference check
and deleting any list carrying a rule answers **500**. The e2e suite's own
teardown is what found this - nothing else had ever deleted a list with a rule
on it.

**Lifecycle.** Deleting a rule leaves nothing pending - generation is just-in-time
and there is no queue - and the items it already made stay as ordinary items with
`rule_id` nulled by the FK action. The UI says so, because the other reading
(deleting the rule deletes its items) is what a member might expect. Deleting the
list cascades the rules and their cost values, and `ListDeletedEvent` must also
forget the rules' pictures. A field becoming required leaves a rule incomplete,
caught at generation and surfaced in `last_error`; deleting a cost choice a **rule**
selected must raise `CostChoiceInUseException` (409) like an item's, or the existing
in-use check lets it through to a raw constraint violation (500).

**An assignee who loses access** is the failure that would silently stop recurrence
for the whole household, and it is defended twice: `ListEventListener.onAccessRevoked`
is extended to clear the rule's `assignee_id` and `assigned_by` for that list
(through the existing synchronous event, `@Transactional(propagation = MANDATORY)`
like its siblings), **and** the materialiser generates *unassigned* with a
`last_error` rather than throwing - because one rule throwing inside a shared
transaction would roll back every other rule's work.

**No caller, and no pretending there is one.** The scheduled run has no principal,
and `SecurityContextHolder.setContext` with a synthetic one is rejected: it hands a
job the standing of an authenticated subject, and anything downstream reading
`requireUserId()` then acts as a user who did not ask.
`SecurityContextCurrentUserProvider`'s whole design is "fail rather than fall back".
So the materialiser **must not call `TodoItemService.create`**, which calls
`requireCurrentUser()`. The seam: extract the field-application logic from
`TodoItemService.apply*` into a collaborator taking an **explicit `AppUser actor`**,
used by both `create` and a new `createFromRule(GeneratedItem)`. Authorization is
already settled by the data - the rule is on the list, so the list is the authority.
`createFromRule` carries a comment saying it is the one entry point that does not
consult `CurrentUserProvider`, because nobody is calling; without it, somebody will
"fix" it.

This extraction is the only non-additive backend work in 0005, and it is also what
bulk assignment wants.

### The schedule, and the single instance

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.recurrence.enabled", havingValue = "true", matchIfMissing = true)
class RecurrenceSchedulingConfiguration { }

@Scheduled(cron = "${app.recurrence.cron}", zone = "${app.household.zone}")
void run() { ... }
```

`@EnableScheduling` goes on the feature's own configuration, not on
`AppApplication`, so the wiring travels with the feature and switching the feature
off switches the scheduler off with it.

```yaml
app:
  household:
    zone: ${APP_TIMEZONE:UTC}
  recurrence:
    enabled: ${RECURRENCE_ENABLED:true}
    cron: ${RECURRENCE_CRON:0 5 1 * * *}
    catch-up-days: ${RECURRENCE_CATCH_UP_DAYS:0}
```

`app.household.zone` is new configuration that nothing today has, and without it a
`(occurrence_on, due_time)` pair becomes an instant in the container's zone - UTC in
every Docker image - so "the bins go out at 7pm" becomes 7pm UTC. `TZ` is set in no
compose file. The dashboard does **not** need it: the client sends explicit instants.

dev and prod use `env_file`, so these need nothing in compose - only documentation in
`deployment/.env.example`. `docker-compose.local.yml` sets environment inline and
gets explicit lines.

**Integration tests must set `app.recurrence.enabled: false`**, or a Testcontainers
run can fire the job mid-assertion and a flake appears months later with no obvious
cause. The materialiser is invoked directly in tests, with an injected `Clock` so
"two runs in one day" and "a run after five days down" are plain unit tests.

What adr/0025 records about the single instance: there is no lock, no leader
election and no `shedlock`; the assumption is one `app` service with one replica,
which both compose files make true; the mitigation if it is ever violated is
`ux_todo_item_occurrence`, so duplicates are impossible and a second instance merely
logs violations; and adr/0003 already notes that in-memory sessions break before
recurrence does, so recurrence is not what blocks a second instance.

### Reading across lists

**Two endpoints, not three and not one.** The three ranges differ only by
`[from, to)`, so three endpoints would be three copies of the one query - and the
access guard is the thing you want exactly one of. But the undated panel sits beside
all three (R45), so folding it into the ranged response would refetch it on every
tab switch.

`from` and `to` are **instants supplied by the client**, half-open. The client
computes its own local midnights, which is right per device, needs no server zone,
and means a member who travels sees their own "today".

```
SELECT i FROM TodoItem i
WHERE i.done = FALSE
  AND i.dueAt >= :from AND i.dueAt < :to
  AND (:mineOnly = FALSE OR i.assignee.id = :userId)
  AND EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId)
ORDER BY i.dueAt ASC, i.list.name ASC, i.position ASC
```

and the same with `i.dueAt IS NULL` for the undated panel. The `EXISTS` guard is the
subquery `TodoListRepository.findVisibleById` already uses, in SQL, never in Java -
and it is why the negative cases belong in the integration suite: a predicate
applied after a full fetch passes every unit test and leaks the household's private
lists onto one member's dashboard. Both queries take a `Pageable` cap of 500 so a
month view cannot become unbounded, and the undated response carries `total`
separately so the panel can say "and 37 more" - which it will, because after V909
every list declares a due date and nobody has filled most of them in.

Indexes: `ix_todo_item_due (list_id, due_at) WHERE done = FALSE` serves both the
range and the `IS NULL` panel after the `list_access` semi-join;
`ix_todo_item_assignee_due` serves `scope=mine`, which does not start from a list.
Confirm with `EXPLAIN` against seeded dev rather than assuming - the planner may
well prefer a sequential scan at household row counts, which is fine, and saying so
here stops somebody "fixing" the plan later.

`DashboardItemResponse` is `ItemResponse` plus the list's identity - `listId`,
`listName`, `listIcon`, because the dashboard crosses lists so every row must say
which list it came from, and the icon is how a list is recognised at a glance
(R18's own argument). It carries the full payload even though the week view shows
only a description, because the popup must open instantly on a phone and the
alternative is a request per tap; a month is a few dozen rows and the HTML is small
(pictures are `/api/images/{id}` references, not bytes).

**Everything readable, with a `scope=mine` toggle, defaulting to `all`.** `ASSIGNEE`
is an optional field whose `required` defaults false, so on most lists most items
have no assignee and on a list that never declared the field *every* item has none.
A mine-only dashboard is therefore empty on the lists this product is about, and it
hides exactly the unclaimed chores a household dashboard exists to surface.
`scope` lives in the URL so it is deep-linkable and adds no state, and R46 is what
finally makes `scope=mine` worth having.

### Activity

A new endpoint rather than an extension of points. `GET /lists/{id}/points` is
all-time standing (adr/0012's derived leaderboard) and feeds `PointsPanel`; activity
is a window and a feed. Folding them would make one response mean two things and
force the leaderboard to refetch whenever the window changed.

```
SELECT i.completedBy.id, i.completedBy.displayName, COUNT(i), COALESCE(SUM(i.pointsEarned), 0)
FROM TodoItem i
WHERE i.list.id = :listId AND i.completedBy IS NOT NULL
  AND i.completedAt >= :from AND i.completedAt < :to
GROUP BY i.completedBy.id, i.completedBy.displayName
ORDER BY COUNT(i) DESC, i.completedBy.displayName ASC
```

plus the events themselves, ordered `completedAt DESC` with a cap. Both are served
by `ix_todo_item_activity (list_id, completed_at DESC) WHERE completed_at IS NOT
NULL`; the existing `ix_todo_item_completer (list_id, completed_by)` cannot answer a
time range, so the new index is not optional. `from` defaults to seven days ago so a
bare request works.

Note the deliberate choice to key activity on `completed_at` rather than on `done`:
a reopened item's completion **still appears** in the week's activity, because it
happened, and R49 kept the record precisely so it could be seen. The points column
will differ from the count for exactly those rows, which is the honest presentation.

The Activity tab renders the existing `PointsPanel` (all-time) above the seven-day
feed. "This week" and "ever" are different questions and a household asks both.

### Bulk assignment

```
POST /api/lists/{listId}/items/assignment
  { assigneeId: uuid|null, items: [{ id, version }] }
  -> 200 { assigned: [ItemResponse], conflicts: [{ id, error, item: ItemResponse|null }] }
```

Scoped to the list, not to `/api/items`, so there is **one** readable check and
**one** assignee-audience check (R24); a cross-list bulk assign would need N of each
and could silently span lists the caller sees differently. Generalised to
`assigneeId` (null unassigns) rather than hard-coding "me", because it is the same
code path; `assigned_by` is always the caller, which is what makes `assigneeId ==
caller` render as self-claimed.

**Partial success, always 200.** All-or-nothing matches the single-item contract, but
it means a member who selected eight items and had one touched by somebody else -
possibly a description edit with nothing to do with assignment - must re-select all
eight on a phone. Their intent for the other seven is unambiguous. 200 rather than
207 because `api/http.ts`'s `isConflict` branches on 409 and a 207 would be a third
branch nothing in the SPA has; the body is the contract here, not the status.

`requireFreshView` throws, so it cannot be reused: a non-throwing `isFreshView`
skips stale items into `conflicts` without mutating them. That covers the common
case - a stale tab - deterministically, which is design/0004's mechanism (1). The
rare true race, mechanism (2), cannot be isolated per item without flushing inside
the loop, and a failed flush poisons the session; so **a genuine concurrent flush
fails the whole batch** with a plain 409 carrying no item, which the client's
existing "409 without an item means invalidate and refetch" path already handles.
Saying that plainly is better than a flush-per-item loop that looks safe and is not.

An id that is not on this list becomes a `conflicts` entry with `item: null` and the
ordinary "no longer here" prose - never a distinct status, because an id on a list
the caller cannot see must answer as a deleted one does (R19).

### Concurrency

**A run moves the rule's version**, because it records when it last ran. A member
who runs a rule by hand and then edits it is editing a stale view, so the screen
refetches the rules after a run as well as the items.

Unchanged mechanisms, three new applications. Every new mutating request carries the
version its view was drawn from and is checked before anything is mutated. A rule is
a child of its list in the sense that matters - the owner edits both - so writing a
rule calls `requireManagedAndFresh` on the list's version *and* carries the rule's
own `@Version`, because two owners is impossible but two tabs is not. Completing with
a note is one write, so the note cannot land on a different version than the
completion did. `markChanged` is **not** needed for rules: `recurrence_rule` is a
separate aggregate with its own version, unlike `list_field`.

### Images and rich text

The completion note goes through the same `HtmlSanitizer` and the same policy
(adr/0018); nothing is sanitized client-side, as before.

`claimDetailImages` becomes `claimImages(item, uploaderId)` and scans the
**concatenation** of `detailHtml` and `completionNoteHtml`, and it must start being
called from the completion path - it is called only from `create` and `update`
today. If it is not changed, in the order the bugs would be found:

1. The note's `stored_image` rows keep `item_id IS NULL`, so `ItemImageAccess` is
   never consulted and the bytes are readable only by the uploader - **every other
   member sees broken pictures in the note**, the exact bug `V907`'s own header
   comment describes.
2. `forgetPicturesOf` keys on `item_id`, so deleting the item leaves the bytes in S3
   forever, counted by `ix_stored_image_unclaimed` and swept by nothing.
3. Deleting the list leaks them too, because `findForList` joins through `item_id`.
4. They stay readable by their uploader even after that member loses access.

A rule owns its pictures through `stored_image.rule_id`, and `claimImages` filters
out images with a `rule_id` so a generated item cannot claim its rule's picture out
from under it. Without that filter, the *first* generated item claims the bytes,
deleting that item forgets them, and **every future occurrence shows a broken
image** - a bug that appears weeks after the rule was written.
`StoredImageRepository.findForList` gains the rule branch, or a deleted list's rule
pictures outlive it.

Residual, already true and now broader: nothing unclaims an image removed from HTML.
Under R49 a reopened item keeps its note, so nothing is orphaned by a reopen at all.
Named here so it is not rediscovered as a bug.

## Frontend

```
/dashboard                      DashboardPage      Tabs + Outlet + UndatedPanel
  index                         -> /dashboard/day
  /dashboard/day                MyDayView          due today, full rows
  /dashboard/week               WeekView           Mon..Sun as seven columns
  /dashboard/month              MonthView          a 6x7 grid
  (any view, ?item=<id>)        DashboardItemDialog

/lists/:listId                  ListDetailPage     header + Tabs + Outlet
  index                         ItemsTab           -> ItemsSection
  /lists/:listId/recurring      RecurringTab       -> RecurrenceSection
  /lists/:listId/activity       ActivityTab        -> PointsPanel + ActivityFeed
```

Tabs are bound to the **pathname**, not to tab state - deep-linkable, correct under
the back button, nothing to keep in sync. `/lists/:listId` stays the index route so
every existing link, `SideNav`'s selected check and the e2e suite's URL handling
survive. `scope` stays a search param because it filters each view rather than being
a different view.

A **Dashboard** entry goes above **Lists** in `SideNav`'s `MENU` section, and the
landing page moves in **three** places that must move together:
`web/public/manifest.webmanifest`'s `start_url`, the catch-all `Navigate` in
`web/src/App.tsx`, and the post-login fallback in `web/src/pages/LoginPage.tsx`. And
the one that will bite: `e2e/conftest.py`'s `ui_sign_in` waits on a `Lists` heading
and **all nine test modules depend on it** - design/0004 warned about this exact
line. It becomes the dashboard's `h1` in the same commit, and `AllListsPage` keeps
its own `h1` for the list-page tests.

### Rows that open

`ItemsSection` holds `expandedId: string | null` - one id, so "at most one expanded"
(R38) is true by construction rather than by cleanup, and `ItemRow` stays the
stateless component it is today, receiving `expanded` and `onToggleExpand`. This is
behavioural state in a component, which adr/0015's rule permits; that rule is about
layout state and `useMediaQuery`, both of which stay in `AppShell`.

The description becomes a `ButtonBase` carrying `aria-expanded` and `aria-controls`,
with the selection checkbox and the action buttons as siblings **outside** it - a
row-wide button containing a checkbox is invalid nesting and a screen reader reads it
as neither. The body is a `Collapse` holding the detail, the completion note, who
completed it and when (R36), and the cost chips.

A welcome side effect: `dangerouslySetInnerHTML` and the note's images leave the
collapsed render path entirely, so a long list draws no pictures. A test cost too -
`test_items.py`'s detail assertion now needs an expand click.

### Completing, claiming and selecting

`ItemsSection` holds `selected: Set<string>`, pruned on every `items` change so ids
that vanished do not linger, and cleared when the status filter changes. When it is
non-empty a selection bar appears above the list with "n selected", "Assign to me"
and "Clear".

The completion control is a **split button**: `Complete`, plus a small `IconButton`
whose accessible name is "Complete … with a note", which opens the note dialog on an
ordinary click. The hold is then an accelerator to the *same* dialog -
`onPointerDown` arms a 1000ms timer, `onPointerUp` / `onPointerLeave` /
`onPointerCancel` clears it, and a `pointermove` beyond ~10px clears it too or
starting a scroll on the button completes an item. `onContextMenu` is prevented and
`touch-action`, `user-select` and `-webkit-touch-callout` are set, because **on iOS a
long press on a button raises the selection callout**, which would land on top of
the dialog that just opened.

Why a split button rather than hold-only: a hold has no keyboard equivalent, so
hold-only fails WCAG 2.1 SC 2.5.1, and neither an `aria-describedby` hint (which
tells but does not enable) nor an invented chord (invisible to everyone) fixes it.
The visible note button *is* the single-pointer alternative, nothing depends on the
gesture, and the tests assert the button while the gesture gets one fake-timer test.

**The selection checkbox is named "Select …", never anything containing "Complete".**
If it were, `getByRole('checkbox', { name: 'Complete Buy milk' })` would keep
matching and the suite would stay green while asserting that ticking the selection
box completes the item. A passing test that tests nothing is worse than a failing
one. The role change breaks four assertions in `e2e/tests/test_items.py` and six in
`ItemsSection.test.tsx`, all in the same phase.

Reopen (R49) is a button on an expanded completed row, and it says that the points go
back - it is destructive enough to say so rather than surprise somebody.

The note dialog mounts a **fresh** `DetailEditor` per item, keyed on the item id.
`DetailEditor`'s `value` is initial-only and never re-synced, so without that key an
editor opened on a second item shows the first item's note - one member's sentence
on another item.

### The add form

`ItemForm` gains `expanded` and renders the description plus **every field whose
`required` is true**, with the rest inside a `Collapse` (R39). Keying off `required`
rather than a fixed list is what stops the form becoming unsubmittable when a hidden
required field has no value. Pleasantly, V909 backfills `required = false`, so the
collapsed form on an existing list still shows only the description.

### Dates without a date library

`web/src/dashboard/calendar.ts`, hand-rolled, with its own test file. What is needed
is start-of-day, start-of-week, add-days, start/end of month and a 6x7 grid - about
forty lines - and all the *formatting* is already `Intl` (`ItemRow.formatDue` uses
`toLocaleString` and is right).

`@mui/x-date-pickers` is rejected: it is a separate MUI package on its own release
train, it needs an adapter **plus** one of date-fns / dayjs / luxon, it is well over
100KB, and pinning a second MUI train against `@mui/material` 9.5.0 is exactly the
drift hazard CLAUDE.md names. It would also replace `<TextField
type="datetime-local">`, which is native, accessible, free, and already a real
picker on a phone.

Two things decided here rather than left to a library:

- **Weeks start Monday**, hard-coded. `Intl` exposes first-day-of-week only through
  `Intl.Locale.prototype.getWeekInfo`, which is not universally available. One
  household, one locale, one line.
- **Adding a day is `d.setDate(d.getDate() + 1)`, never `+ 86400000`.** The
  millisecond version is an hour wrong twice a year and surfaces as a week view
  whose Monday column is empty. A test pins `TZ` to a DST-observing zone and steps
  across spring-forward.

### The self-claimed badge

A named semantic palette entry `selfClaimed` in `theme.ts`, in both colour schemes,
via module augmentation. Not `secondary` - MUI's generic second brand colour, which
unrelated things will reach for within a release - and not `primary`, which
`ItemRow` already uses for the completed chip, so reusing it would give one colour
two meanings, the drift adr/0014 exists to prevent.

And colour is **not** the only signal (R47): the chip carries a small person-check
icon and an accessible name of the form "Ana claimed this", so a colour-blind
member, a screen reader and a Playwright role query all work from the same fact.
Colour alone would fail WCAG 1.4.1 and be unassertable.

## Testing

- **Unit (backend)** - `RecurrenceCalculator`, where the bugs live and which is
  pure: DAILY every day and every third day from `starts_on`; WEEKLY on Mon+Thu
  giving exactly two dates a week and nothing in a skipped interval week; WEEKLY
  with `interval_count = 2` anchored on `starts_on`'s week rather than the ISO week
  number; MONTHLY on the 31st clamping to 28, 29 and 30; `ends_on` inclusive and a
  spent rule generating nothing; a paused rule generating nothing; an empty range
  when `from > to`. The materialiser: a second run the same day creates nothing; a
  run after five days down with `catch-up-days = 0` creates only today forward; an
  occurrence the unique index already holds is counted `skipped`, not thrown; the
  generated item carries the template and **null** points, completion columns and
  note; `created_by` is the rule's author; **no `CurrentUserProvider` is consulted**
  - asserted by injecting a provider that throws on every method, the one test that
  catches a future "just set the security context" fix; an assignee who lost access
  generates unassigned with a `last_error`; one failing rule does not stop the next.
  Completion: `completeBy` stores a sanitized note; `reopen()` sets the stamp,
  returns the points and keeps the note; `done` is false after a reopen and true
  after completing again; a note on a reopen is a 400 before anything mutates; the
  note goes through the sanitizer (`script`, `onerror`, an off-site `img`,
  `javascript:`, a surviving bullet list). `claimImages` scans **both** columns: a
  picture referenced only from the note is claimed, one uploaded by another member is
  not, one already owned by a rule is not, and a note-only picture is returned by
  `findForItem` so `forgetPicturesOf` reaches it - the assertion that would have
  caught the leak. Bulk assign: all-fresh assigns all and sets `assigned_by`; one
  stale version lands in `conflicts` carrying the server's item while the rest
  commit; an assignee without access is a single 400 before any write; an id from
  another list is a conflict with `item: null`; `assigneeId: null` clears both
  columns; `selfAssigned` is true only when assignee equals `assigned_by`. Field
  config: `DUE_AT` is injected when omitted, refused when deleted, and `required` is
  kept as sent; deleting a cost choice a **rule** uses is a 409, not a 500.
- **Unit (frontend)** - `calendar.ts`: start-of-week on a Sunday and a Monday; a
  month grid of exactly 42 cells with the right leading and trailing days;
  `addDays` across spring-forward with a pinned `TZ`. `ItemRow`: collapsed shows
  neither detail nor note, expanded shows both, `aria-expanded` and `aria-controls`
  move together, and expanding a second row collapses the first. The split button: a
  click completes; the note button opens the dialog **without** completing; with
  fake timers an 1100ms hold opens the dialog and does not complete, a 400ms hold
  completes, and a 20px move during a hold does neither; and the dialog is reachable
  by keyboard alone - the 2.5.1 alternative asserted as behaviour. The note dialog
  mounts a fresh editor per item: type on A, close, open B, assert B is empty.
  Multi-select: the checkbox's name is "Select …" and **nothing matches "Complete …"
  as a checkbox** - the test that stops the silent-green regression; "Assign to me"
  posts one request with every id and version; a partial response clears the
  succeeded ids, keeps the conflicted ones, writes the server's items into the cache
  and raises one snackbar naming the count. `ItemForm` collapsed shows the
  description and every required field and nothing else. The dashboard: My day
  renders only items in range; Week renders seven day columns with descriptions and
  no chips; Month renders 42 cells; the undated panel renders on all three and does
  **not** refetch on a tab change (assert the spy's call count); `?item=` opens the
  popup and back closes it; `scope=mine` reaches the request. The self-claimed chip
  is identified by its accessible name, never by colour.
- **Integration** - real Postgres, because a Java-side predicate passes every unit
  test and leaks every list. The access guard on all three new read paths: a
  stranger's dashboard contains none of another member's items for either scope; the
  **admin's** dashboard contains nothing from an ungranted list, the test that stops
  a future `|| isAdmin()`; activity is 404 for a stranger; revoking a grant removes
  the items from that member's dashboard inside one test. The extended
  `ck_todo_item_completion`: a direct `UPDATE` putting a note on an open item is
  refused by the database, not merely by the service, and a `reopened_at` before its
  `completed_at` is refused. The **absence** of the assignment CHECK proved useful:
  delete an account that is both an assignee and an `assigned_by` and assert the
  delete succeeds - the test that would have caught the 500. `ux_todo_item_occurrence`:
  two materialiser calls for one `(rule, date)` in separate transactions yield one
  item and one counted skip. V909 against a list already holding four fields lands
  `DUE_AT` at position 4 and commits with the deferred unique constraint, and a list
  that already declared it keeps `required = true`. `ddl-auto: validate` passes with
  the `TIME`, `DATE` and generated-column mappings - expect one iteration here, loud
  at startup. Cascades: deleting a list removes its rules, their cost values and
  their pictures' **bytes**; deleting a rule leaves its items with `rule_id` null.
  The scheduler is **off** in the integration profile, asserted by the absence of a
  registered scheduled task so a future context change cannot reintroduce a job
  firing mid-assertion. `POST /api/admin/recurrence/runs` is 403 for a member, 200
  for the admin, and its body contains **only counts** - no list name, no
  description, no rule id, adr/0010's leak asserted.
- **E2E** - new markers `recurring`, `activity`, `dashboard` in `e2e/pyproject.toml`
  (`--strict-markers` rejects unregistered ones). Existing changes land in the same
  phase as the code: `ui_sign_in`'s heading, the completion control's role, an expand
  click before the detail assertion, a tab click before the points assertion, and the
  removal of the due-date switch click from `test_items.py` and `test_fields.py`
  (the field is no longer switchable; the required checkbox stays). New scenarios,
  two contexts where it matters: **completion detail across two members** - Ana
  completes with a note and a picture, Ben expands the row and sees the note, the
  picture and Ana's name, the picture being the assertion that proves the note's
  image was claimed; **reopen returns the points** - Ana completes a 50-point item,
  Activity credits her, Ben reopens it, the points are gone and the row is
  actionable, and the completion still appears in the week's activity; **the hold is
  not the only way** - reach the note dialog by keyboard alone, since a real browser
  is the only place that question is answerable; **claiming in bulk** - select three,
  Assign to me, all three carry the claimed badge, while the second context changes
  one first and the snackbar says one was missed; **a recurring item appears** -
  create a daily rule, fire the per-list run, the item is there, fire it again and
  there is still one; **activity is this week** - two members complete on a shared
  list and both appear with the right counts; **the dashboard is bounded by access**
  - a private list's item never appears on the other member's dashboard in any view,
  and an item due today appears in My day and in this week's column.

Phases, and what is independently deployable: **A** V908, V909 and the write path
(backend-additive; the only visible change is a due-date input appearing on lists
that lacked one). **B** R36-R39, R46-R49 - the row rework, **atomically with every
selector change in `ItemsSection.test.tsx` and `test_items.py`**, because `e2e/` runs
against dev and nothing else may ride along. **C** the tabbed list page and Activity
(additive; one existing assertion needs a tab click). **D** recurrence, shipped with
`RECURRENCE_ENABLED=false` and triggered by hand on dev before the cron is let
loose. **E** the dashboard, last and alone, because it moves the landing page and
every e2e module depends on that - so it is also the cheapest thing to revert.
