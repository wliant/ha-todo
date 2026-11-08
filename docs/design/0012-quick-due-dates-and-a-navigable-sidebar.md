# 0012 - Quick due dates and a sidebar that reaches every list

Design for [requirements/0012](../requirements/0012-quick-due-dates-and-a-navigable-sidebar.md)
(R94-R106). Two independent features that ship together because both are small
and both are in the path a member walks every day.

Decisions this implements rather than argues:
[adr/0041](../adr/0041-interaction-frequency-is-a-pruned-daily-bucket.md),
[adr/0042](../adr/0042-a-nav-group-owns-its-own-disclosure.md),
[adr/0043](../adr/0043-a-collection-can-be-pinned.md).

## Scope

Two tracks, in this order, because the second is the one with a migration:

1. **The due-date control** (R94-R97). Frontend only. One new component, one
   deletion from `ItemForm`.
2. **The sidebar** (R98-R106). Two migrations, four ordered reads, a touch
   endpoint, a collection pin, one preference, and `SideNav` rebuilt.

## What is already there

| Piece | Where | Why it is enough |
| --- | --- | --- |
| Household-zone date arithmetic | `dashboard/calendar.ts` | `civilIn`, `instantOf`, `addDays`, `dayKey`, `now` - and no function in it reads the device |
| The zone itself | `app/useHouseholdZone.ts` | A hook, so the control cannot reach for the device out of habit |
| The visibility join | `list_access` / `data_list_access` | The sidebar's ordering query filters in SQL, as every other list read does |
| Per-member list state | `list_user_state` | Pins (R101) and the recency tie-break (`adr/0041`) |
| A preference store | `AppUser.themePreference`, `/api/me/preferences` | R102 is a second column on a row that already exists for this |
| The touch convention | `POST /api/lists/{id}/touch` | A POST, not a GET side effect, so a prefetch cannot reorder a sidebar |
| The icons | `lists/listIcons.tsx` | Collections already draw from the same closed set |

## Track 1 - the due-date control

### `items/DueAtField.tsx`

Replaces the bare `datetime-local` in `ItemForm`. One component, because the
recurrence form will want it next and because the zone conversion must exist
once.

```
[ Today ] [ Tomorrow ]                       [ Clear ]
[ − ]  [ date: 2026-11-08 ]  [ + ]   [ time: 23:59 ]
```

**The ISO instant stays the single source of truth.** The component takes
`value: string | null` and emits one; it holds no copy of the date. Everything
on screen is derived:

```
civil = value ? civilIn(new Date(value), zone) : null
date  = civil ? dayKey(civil)  : ''
time  = civil ? hhmm(civil)    : pendingTime
```

so an external change to `value` - loading an item for edit, a reset after save
- cannot leave the control showing something the form is not holding. The one
piece of local state is `pendingTime`: the time a member typed *before* picking
a date, which has nowhere else to live. It seeds from `DEFAULT_TIME` (`23:59`),
which is R95.

**Emitting is the only place a zone is used:**

```
emit(dateKey, timeText) =
  instantOf(new Date(y, m - 1, d, hh, mm), zone).toISOString()
```

`instantOf`, not `new Date(local)`. That single substitution is R96: the same
keystrokes now mean the household's Thursday rather than the device's.

- **Today / Tomorrow** are `addDays(now(zone), 0 | 1)` - the household's today,
  not the device's - and render selected when the current date matches, so the
  chips are also the readout.
- **− / +** step `addDays(civil, ±1)` and are **disabled with no date set**.
  A stepper with no value to step would have to invent one, and "+" meaning
  "today" is not something anybody would guess.
- **Clear** renders only when the field is not required (R97).
- The `date` input stays native, so the picker and the keyboard both still work;
  only the time is pre-answered.

`ItemForm`'s `toLocalInput` / `fromLocalInput` are **deleted**, not left for
reuse. They are precisely the device-zone conversion R96 removes, and a helper
named `toLocalInput` sitting in the file is an invitation.

## Track 2 - the sidebar

### Step 1 - the migrations (V917, V918)

```sql
CREATE TABLE list_interaction_day (
    list_id UUID, user_id UUID, on_day DATE, hits INT, last_at TIMESTAMPTZ,
    PRIMARY KEY (list_id, user_id, on_day));
CREATE INDEX ix_list_interaction_day_prune ON list_interaction_day (user_id, on_day);
-- and the same shape for data_list_interaction_day
ALTER TABLE app_user ADD COLUMN sidebar_list_count INT NOT NULL DEFAULT 10;
```

`on_day` is a `DATE` the application supplies in `app.timezone` - see
`adr/0041` for why it is not generated. The prune index is on
`(user_id, on_day)` and not `(list_id, ...)`, because the prune is scoped to the
member.

V918 gives collections a pin (R104), in a table of their own because
`list_user_state.list_id` is an FK to `todo_list` and `adr/0029` keeps it that
way:

```sql
CREATE TABLE data_list_user_state (
    data_list_id UUID, user_id UUID, pinned_at TIMESTAMPTZ,
    PRIMARY KEY (data_list_id, user_id));
```

Deliberately thinner than `list_user_state`: **no `last_interacted_at` twin**. A
collection's recency comes from its interaction buckets, and a second column
holding it is a second thing to keep true. See `adr/0043`, which also records
which earlier statements this overrides.

### Step 2 - the writes

`ListInteractionRepository.record` and its data-list twin, one native statement
each:

```sql
INSERT INTO list_interaction_day (list_id, user_id, on_day, hits, last_at)
VALUES (:listId, :userId, :onDay, 1, now())
ON CONFLICT (list_id, user_id, on_day) DO UPDATE
   SET hits = list_interaction_day.hits + 1, last_at = now()
 WHERE list_interaction_day.last_at < now() - make_interval(mins => :windowMinutes)
```

The `WHERE` on the `DO UPDATE` is the fifteen-minute window, read from the row
being written - which is what makes this independent of `list_user_state` and
therefore usable by collections (`adr/0041`). A repeat visit inside the window
takes no lock and writes no WAL, exactly as the recency upsert does.

`prune` follows it: `DELETE ... WHERE user_id = :userId AND on_day < :cutoff`.

`TodoListService.touch` calls both after its existing recency upsert.
`DataListService.touch` is new and calls both - there is no recency row to
write.

### Step 3 - the ordered reads

`TodoListRepository.findForSidebar`, native because of the `NULLS LAST` and the
aggregate join:

```sql
SELECT l.* FROM todo_list l
JOIN list_access a ON a.list_id = l.id AND a.user_id = :userId
LEFT JOIN list_user_state s ON s.list_id = l.id AND s.user_id = :userId
LEFT JOIN (SELECT list_id, SUM(hits) AS hits FROM list_interaction_day
            WHERE user_id = :userId AND on_day >= :since GROUP BY list_id) f
       ON f.list_id = l.id
ORDER BY (s.pinned_at IS NOT NULL) DESC,
         COALESCE(f.hits, 0) DESC,
         s.last_interacted_at DESC NULLS LAST,
         LOWER(l.name) ASC
LIMIT :limit
```

- The `JOIN` on `list_access` is the access filter, in SQL, as every list read
  is. The owner holds a grant of their own, so the join alone answers it.
- Both outer joins are **LEFT**, which is R98: a list with no state row and no
  buckets still comes back, sorted among the other zeroes by name. A reader who
  "tidies" either into an inner join restores the old only-what-you-opened
  behaviour, silently.
- `WHERE s.pinned_at IS NULL` is R105: a pinned list is drawn in its own section
  above, so it leaves the group. That is also what keeps the cap honest - a
  member who pins twelve things still sees a group underneath them rather than
  a cap spent entirely on pins.
- `:since` is `today - 30 days` in the household's zone, computed by the
  service. It is **not** a parameter of the endpoint, for `adr/0040`'s reason:
  a window a caller supplies is a window that drifts.

The data-list query is the same shape with `MAX(last_at)` as the tie-break,
since a collection has no `last_interacted_at` of its own.

Two more reads, one per aggregate, for the pinned section:

```
findPinnedForSidebar(userId) -- pinned_at IS NOT NULL, same access guard,
                             -- ORDER BY LOWER(name), no LIMIT
```

**Uncapped** is R106 - the section scrolls rather than truncating. **By name**
rather than by `pinned_at` because the two halves arrive from two requests with
no shared clock to interleave on, and the alternative is a per-member timestamp
on DTOs the whole app reads (`adr/0043`).

### Step 4 - the endpoints

- `GET /api/lists/sidebar` keeps its path and changes its body to
  `SidebarResponse(pinned, lists)`. Still two components, but they now
  **partition** rather than overlap: `lists` excludes everything in `pinned`.
  One request per aggregate, because a split that could disagree while one half
  was in flight would draw a list in both sections or in neither.
- `GET /api/data-lists/sidebar` is new, in `DataListController`, the same shape.
- `PUT` / `DELETE /api/data-lists/{id}/pin` are new (R104), checking
  **readable** rather than **managed**, exactly as a list's pin does: a pin is
  the caller's own note about something they can see.
- `POST /api/data-lists/{id}/touch` is new.
- `PATCH /api/me/preferences` grows `sidebarListCount`, bounded `1..30`.
  `Integer` with a defaulting accessor, not `int`, because Jackson rejects a
  missing primitive in a record and "not mentioned" must keep meaning
  "unchanged".

### Step 5 - `SideNav`

The Recent section is **deleted**; Pinned stays and grows to hold both kinds:

```
Pinned                      (max-height 15rem, scrolls - R106)
  <icon> Chores                 lists and collections, merged by name
  <icon> Recipes

Menu
Agenda
Lists         ▾   +        -> /lists
  <icon> Groceries              everything NOT pinned, ordered by adr/0041
  ...
Collections   ▾   +        -> /collections
  <icon> Cooking
Users (admin only)
```

`PinnedSection` renders **nothing at all** when empty - unlike the two groups
below, which say "no lists yet" because they are permanent fixtures carrying a
create button. An empty labelled Pinned section is noise. It takes a `loaded`
flag so it does not flash in on first paint: with no data yet, its entries are
empty for the same reason they are empty for a member with no pins, and the two
must not look the same.

One `NavGroup` component drawn twice for the groups. Their open state lives in
`SideNav` as `open: { lists: boolean; collections: boolean }`, both true by
default (`adr/0042`). The chevron is a separate `IconButton` **beside** the
`ListItemButton`, not inside it - the group's label still navigates (R99), and a
button inside a button is the invalid nesting `adr/0039` already had to undo
once.

An empty group renders one row saying so, with the group's own create
affordance beside the header (R103).

### Step 7 - the star on both kinds

`PinButton` stops reaching for `usePinList` and takes an `onToggle` instead.
`ListCard` and `ListDetailPage` wire `usePinList`; `CollectionCard` and
`CollectionDetailPage` wire the new `usePinCollection`. A button that picked
between two hooks would have to call both on every render to keep hook order
stable, and the caller already knows which kind it draws.

`repin` - the optimistic flag rewrite - moves to `app/repin.ts`, shared by both
query modules. It handles a bare array of summaries and a sidebar envelope, and
distinguishes the two by `typeof record.pinned === 'boolean'`: a summary's
`pinned` is a boolean, an envelope's is an array. It deliberately does **not**
move the entry between the envelope's arrays - where it belongs depends on an
ordering only the server computes, so the star flips instantly and `onSettled`
relocates the row.

### Step 6 - the preference

`ProfilePage` grows a number field beside the theme control. The sidebar reads
it from `/api/me/preferences`, which the shell already fetches for the theme.

## Testing

| Requirement | Where |
| --- | --- |
| R94, R97 | `DueAtField.test.tsx` - chips, steppers, clear |
| R95 | the same, asserting `23:59` on a freshly picked date |
| R96 | the same, with the household zone set away from the test runner's |
| R98 | `ListApiIntegrationTest` - a just-created list appears |
| R100 | the same - a list opened three times outranks one opened once later |
| R102 | `MeServiceTest`, the bound rejected, and the cap read through the endpoint |
| R104 | `DataListApiIntegrationTest` - personal, 404 without a grant |
| R105 | both suites - a pin moves the row out of the group, and back |
| R106 | `ListApiIntegrationTest` - four pins survive a preference of two |
| R99, R103, R105 | `SideNav.test.tsx` |
| end to end | `e2e/tests/test_sidebar.py`, plus the due-date fills already in `test_dashboard.py` and `test_fields.py` updated to the new control |

## What this deliberately does not do

- Order `/lists` by use. The index is a browse (R-out-of-scope).
- Persist the collapsed state. `adr/0042`.
- Give collections a `last_interacted_at`, a "Pinned only" filter on the
  collections index, or an interleaved-by-pin-time section. `adr/0043`.
- Count anything but a visit. A second signal is a second thing to keep true.
