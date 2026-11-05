# 0004 - Lists, items, and personal settings

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R18-R35
Builds on: [design/0003](0003-pwa-and-ui-shell.md)
Amends: [design/0001](0001-skeleton-architecture.md) - its "Household scoping (for
when the domain arrives)" paragraph is replaced by per-list access, below.
[design/0003](0003-pwa-and-ui-shell.md) - its responsive navigation rules are
replaced by the sidebar.

## Scope

The domain: lists, who can see them, what shape their items take, the items
themselves, completion and points. Plus the two personal surfaces the sidebar
exposes - a profile with an image and a password, and an appearance preference.

Out of scope: everything in requirements/0004's "Explicitly not required", and
notably per-grant roles, ownership transfer, recurring items and notifications.

Decisions behind this design:
[adr/0010](../adr/0010-per-list-access-instead-of-household-scoping.md) (per-list
access, and 404 for an ungranted list),
[adr/0011](../adr/0011-item-fields-as-a-typed-per-list-declaration.md) (typed field
rows, merge-on-id),
[adr/0012](../adr/0012-cost-and-points-credited-to-the-completer.md) (cost modes,
points snapshotting),
[adr/0013](../adr/0013-minio-for-images-served-through-the-api.md) (MinIO, bytes
through the API),
[adr/0014](../adr/0014-mui-as-the-design-system.md) (MUI, the theme replaces the
tokens),
[adr/0015](../adr/0015-a-sidebar-replaces-the-dual-navigation-bar.md) (one sidebar,
one boolean of layout state),
[adr/0016](../adr/0016-the-authenticated-principal-carries-the-user-id.md) (the
current user in the service layer),
[adr/0017](../adr/0017-members-manage-their-own-password-and-theme.md)
(self-service password, admin reset),
[adr/0018](../adr/0018-detail-html-is-sanitized-on-write.md) (server-side
sanitization),
[adr/0019](../adr/0019-avatars-on-the-public-sign-in-roster.md) (one field added to
the public roster),
[adr/0020](../adr/0020-a-shared-link-becomes-an-item-on-a-chosen-list.md) (a shared
link lands on a chosen list).

## Data

```
todo_list
  id           uuid pk
  owner_id     uuid not null -> app_user     -- manages the list (R20)
  name         varchar(80)  not null         -- unique on (owner_id, lower(name))
  description  varchar(500)
  icon         varchar(40)  not null         -- a ListIcon token
  version      bigint       not null         -- @Version; the config is contended
  created_at / updated_at

list_access                                  -- adr/0010; the owner has a row too
  list_id      uuid not null -> todo_list
  user_id      uuid not null -> app_user
  granted_at   timestamptz not null
  granted_by   uuid -> app_user
  pk (list_id, user_id)

list_field                                   -- adr/0011
  id              uuid pk
  list_id         uuid not null -> todo_list
  kind            varchar(16) not null       -- DESCRIPTION|DETAIL|ASSIGNEE|DUE_AT|COST
  label           varchar(30)                -- required for COST, null otherwise
  required        boolean not null
  position        smallint not null
  cost_mode       varchar(8)                 -- FIXED|NUMERIC, COST only
  cost_unit_label varchar(20)                -- NUMERIC only, e.g. 'dollar'
  points_per_unit numeric(12,4)              -- NUMERIC only, optional

list_field_choice                            -- the choices of a FIXED cost field
  id            uuid pk
  list_field_id uuid not null -> list_field
  label         varchar(30) not null         -- '30 min'; no numeric amount (adr/0012)
  points        integer not null
  position      smallint not null

list_user_state                              -- a member's own state for a list
  list_id            uuid not null -> todo_list
  user_id            uuid not null -> app_user
  pinned_at          timestamptz             -- NULL *is* "not pinned"
  last_interacted_at timestamptz
  pk (list_id, user_id)

todo_item
  id            uuid pk
  list_id       uuid not null -> todo_list
  description   varchar(300) not null        -- the always-on field, so a column
  detail_html   text                         -- sanitized on write (adr/0018)
  assignee_id   uuid -> app_user             -- must hold access (R24)
  due_at        timestamptz
  completed_by  uuid -> app_user             -- earns the points (R23)
  completed_at  timestamptz
  points_earned integer                      -- snapshot; null while open
  created_by    uuid -> app_user
  position      integer not null
  version       bigint not null              -- @Version; the contended write
  created_at / updated_at

item_cost_value                              -- one row per cost field per item
  item_id       uuid not null -> todo_item
  list_field_id uuid not null -> list_field
  choice_id     uuid -> list_field_choice    -- FIXED; ON DELETE RESTRICT
  amount        numeric(12,4)                -- NUMERIC
  pk (item_id, list_field_id)

stored_image
  id           uuid pk
  object_key   varchar(200) not null unique
  content_type varchar(40)  not null         -- png|jpeg|webp, CHECK'd
  size_bytes   integer      not null
  purpose      varchar(16)  not null         -- AVATAR|DETAIL
  uploaded_by  uuid -> app_user
  created_at

app_user  + theme_preference varchar(8) not null default 'SYSTEM'  -- LIGHT|DARK|SYSTEM
          + avatar_image_id  uuid -> stored_image
```

### Invariants, and who holds each

Held by the database, so a bad migration or a hand-run `UPDATE` cannot produce an
invalid row - the habit `ux_app_user_single_admin` established:

- `ux_todo_list_owner_name` - unique on `(owner_id, lower(name))`. Two members may
  each own a "Groceries"; one member may not own two.
- `ux_list_field_singleton` - unique on `(list_id, kind) WHERE kind <> 'COST'`. At
  most one description, detail, assignee and due-date field per list.
- `ux_list_field_cost_label` - unique on `(list_id, lower(label)) WHERE kind =
  'COST'`. Cost fields are many and distinguished by name (R22).
- `ck_list_field_description_required` - `kind <> 'DESCRIPTION' OR required`. The
  unrequired description is unrepresentable rather than merely rejected.
- `ck_list_field_label` - `(kind = 'COST') = (label IS NOT NULL)`. Cost fields are
  named; singletons are not.
- `ck_list_field_cost_only`, `ck_list_field_cost_mode`,
  `ck_list_field_numeric_unit`, `ck_list_field_fixed_has_no_unit` - cost
  configuration exists only on a cost field, and only the parts its mode uses.
- `ck_todo_item_completion` - `(completed_by IS NULL) = (completed_at IS NULL)` and
  points only when complete. The three completion columns move together.
- `ck_item_cost_value_one_of` - `(choice_id IS NULL) <> (amount IS NULL)`. A cost
  value is a chosen label or a measured amount, never both and never neither.
- `item_cost_value.choice_id` is `ON DELETE RESTRICT`: deleting a choice items have
  selected must fail loudly, not null out their cost.

**Position uniqueness is deferred.** `(list_id, position)` on `list_field`, and
`(list_field_id, position)` on `list_field_choice`, are table constraints declared
`DEFERRABLE INITIALLY DEFERRED` rather than plain unique indexes. Reordering fields
rewrites several positions in one statement, and a non-deferred constraint collides
mid-transaction on a perfectly valid final state. This passes every unit test and
fails only against real Postgres, which is why the integration suite reorders a
three-field list in one transaction.

Held by the service, because SQL cannot express them without a trigger - and a
trigger is a worse home for a rule the API already rejects with readable prose:

- A `FIXED` cost field has at least one choice.
- Every list has exactly one `DESCRIPTION` field; the service always writes it on
  create and refuses to remove it.
- The owner always holds access, and their grant cannot be revoked.
- A cost value's `list_field_id` belongs to the item's own list. The foreign key
  only proves the field exists, so an item carrying another list's cost field is
  representable in SQL and must be rejected in code.
- An assignee holds access to the list (R24). Revoking a grant also unassigns that
  member's items on that list.
- A required field has a value.

### Points

Points are computed at completion from the configuration as it stands at that
moment, summed across every cost value on the item - a `FIXED` value contributes
its choice's `points`, a `NUMERIC` value contributes `amount * points_per_unit` -
and written to `todo_item.points_earned`. Un-completing clears it with the other
two completion columns. Editing a rate afterwards does not rewrite history
(adr/0012).

The per-list leaderboard is derived, not counted. There is no `list_points` table:

```sql
SELECT completed_by, SUM(points_earned) FROM todo_item
WHERE list_id = :list AND completed_by IS NOT NULL GROUP BY completed_by
```

covered by `ix_todo_item_completer` on `(list_id, completed_by) WHERE completed_by
IS NOT NULL`. A stored counter would be a second source of truth that disagrees
with the items the first time a delete or cascade path forgets a decrement, and the
query it saves is already trivial at household scale.

### Recency without a write on every read

No `GET` updates `last_interacted_at`. A `@Transactional(readOnly = true)` service
that writes is a trap, and it would make every focus-triggered refetch a row
update. Instead:

1. **Mutations stamp it implicitly**, inside the transaction that was already
   writing - creating a list, renaming it, reconfiguring it, pinning it, and every
   item write. Free.
2. **Opening a list stamps it explicitly**, via `POST /api/lists/{id}/touch`, fired
   once per list per session by the SPA. One statement, no read, with a
   fifteen-minute floor so a repeat visit is a storage-layer no-op that takes no
   row lock:

```sql
INSERT INTO list_user_state (list_id, user_id, last_interacted_at)
VALUES (:list, :user, now())
ON CONFLICT (list_id, user_id) DO UPDATE SET last_interacted_at = now()
WHERE list_user_state.last_interacted_at IS NULL
   OR list_user_state.last_interacted_at < now() - INTERVAL '15 minutes';
```

Rows are keyed per `(list, user)`, so two members opening the same list never
contend. It is a `POST`, so CSRF applies and a prefetch cannot reorder a sidebar.

`list_user_state` is deliberately not columns on `list_access`: a grant is a fact
others assert about you and carries `granted_at`, while this is yours and is
rewritten whenever you open the list. Sharing one row would make a recency write
contend with a grant revoke. Neither table carries `@Version` - last-write-wins is
correct for a pin and a timestamp, and a conflict there would be noise.

### Migration path

Versioned migrations resume at **V902**, continuing the series design/0002
established (V901 is the high-water mark; anything at or below 900 is out of order
on any database that applied the retired versioned seed, which is also why local
and dev set `flyway.ignore-migration-patterns: "*:missing"`).

| Migration | Phase | Contents |
|---|---|---|
| `V902__theme_preference.sql` | 1 | `app_user.theme_preference` + its `CHECK` |
| `V903__lists_and_access.sql` | 2 | `todo_list`, `list_access`, `list_user_state` |
| `V904__list_fields.sql` | 3 | `list_field`, `list_field_choice` |
| `V905__profile_images.sql` | 4 | `stored_image`, `app_user.avatar_image_id` |
| `V906__items.sql` | 5 | `todo_item`, `item_cost_value` |

Each carries a header comment in V901's explanatory style, naming the requirement
and ADR it serves.

`ddl-auto: validate` makes the entity mappings an assertion against this DDL, which
is the point - but it also means `numeric(12,4)` needs explicit
`@Column(precision = 12, scale = 4)` on the `BigDecimal` fields and `timestamptz`
needs the `Instant` mapping Hibernate already uses elsewhere. Expect one iteration
here on the first integration run; it fails at startup, after the migration has
applied, so it is loud rather than subtle.

Seed data stays a repeatable migration. A second file,
`db/seed/R__seed_demo_lists.sql`, adds two demo lists - "Groceries (demo)" granted
to Ana and Ben, and "Ana's workout (demo)" granted to Ana alone. The second one is
what makes the access rule visible the moment you sign in as Ben. It must be
idempotent by looking lists up by name (`INSERT ... WHERE NOT EXISTS`), because
Flyway re-runs a repeatable migration whenever its content changes. Local and dev
only, which the `db/seed` location already guarantees.

## Backend

New packages, feature-sliced as the project already is: `list/`, `item/`, `me/`,
`images/`, `richtext/`, plus `AppUserPrincipal` / `CurrentUserProvider` in `user/`.

```
GET    /api/lists                  authenticated  ?q=&icon=&scope=all|owned|shared&pinned=
POST   /api/lists                  authenticated  -> 201 ListDetailResponse
GET    /api/lists/sidebar          authenticated  -> { pinned[], recent[] }  recent capped at 6
GET    /api/lists/{id}             access
PATCH  /api/lists/{id}             owner          { version, name?, description?, icon? }
DELETE /api/lists/{id}             owner          -> 204
GET    /api/lists/{id}/access      access
PUT    /api/lists/{id}/access      owner          { version, userIds[] }
GET    /api/lists/{id}/fields      access
PUT    /api/lists/{id}/fields      owner          { version, fields[] }   merge by id
PUT    /api/lists/{id}/pin         access         -> 204
DELETE /api/lists/{id}/pin         access         -> 204
POST   /api/lists/{id}/touch       access         -> 204
GET    /api/lists/{id}/points      access         -> [{ userId, displayName, points }]

GET    /api/lists/{id}/items       access         ?status=open|done|all&assignee=
POST   /api/lists/{id}/items       access         -> 201 ItemResponse
PATCH  /api/items/{id}             access         { version, ... }
DELETE /api/items/{id}             access         ?version=  -> 204
PUT    /api/items/{id}/completion  access         { version, completed } -> ItemResponse

GET    /api/users                  authenticated  -> [{ id, displayName, avatarImageId }]
GET    /api/me/preferences         authenticated
PATCH  /api/me/preferences         authenticated  { theme }
PUT    /api/me/password            authenticated  { password? } -> 204, 409 for the admin
PUT    /api/me/avatar              authenticated  multipart -> { avatarImageId }
DELETE /api/me/avatar              authenticated  -> 204
PUT    /api/admin/users/{id}/password  ROLE_ADMIN  { password? } -> 204

POST   /api/images                 authenticated  multipart: file, purpose -> 201
GET    /api/images/{id}            purpose-dependent -> bytes
```

**`SecurityConfig` gains no new rule.** `anyRequest().authenticated()` already
covers every list, item, me and image route, and `/api/admin/**` already covers the
password reset. The only exception is the anonymous read of `AVATAR` images that
adr/0019 requires. This absence is deliberate and is recorded because it looks like
an omission: the real rule is in the service layer, and a `hasRole` line here would
imply the admin has reach into lists, which adr/0010 refuses.

**`GET /api/users` is new and authenticated.** The access picker, the assignee
dropdown and the leaderboard need display names and avatars for everyone. It is not
`GET /api/auth/users`: that endpoint is `permitAll` and adr/0009 forbids growing it
beyond the one field adr/0019 adds.

### Authorization

Two predicates in the service layer, never the controller (design/0001's standing
rule, unchanged in substance):

- `readable(list, me)` - `list.ownerId == me` or a `list_access` row exists. Grants
  reading the list and reading, writing, completing and assigning its items; every
  member with access is a peer (R20).
- `managed(list, me)` - `list.ownerId == me`. Grants the name, icon, description,
  field configuration, access list and deletion. **Not** `|| isAdmin()`.

`GET /api/lists` filters **in the query**, by joining `list_access`. A predicate
applied in Java after a full fetch passes every unit test and leaks every list,
which is why the negative cases are asserted against real Postgres.

**No access is 404; access without ownership is 403.** R19 makes the existence of a
list the thing being hidden, so a 403 for an ungranted list would disclose that the
id is real - the same stance adr/0009 takes on the roster. A member who can already
see the list gets 403 on a management route, because a 404 there would be a lie
their UI would have to work around.

`PUT /api/me/password` takes no user id; it acts on
`CurrentUserProvider.requireUserId()`, so there is nothing to tamper with.

### The current user

`AppUserDetailsService` returns `AppUserPrincipal`, carrying the user's id, so no
request pays a lookup to learn who is calling. `CurrentUserProvider` reads it from
the `SecurityContextHolder` and throws when it is absent or the wrong type - fail
closed, never an anonymous id. `LoginService`'s passwordless branch must build its
authenticated token from the same principal type; if only the
`DaoAuthenticationProvider` path does, password sign-in works and passwordless
sign-in puts an unusable principal in the session. See adr/0016.

### Field configuration is merged, not replaced

`PUT /api/lists/{id}/fields` sends existing field and choice ids back. The service
inserts entries without an id, updates those with one, and deletes only those the
client omitted - refusing to delete a field or choice that items reference.
Delete-and-recreate would change every `list_field.id` and orphan every
`item_cost_value`. This is a data-loss bug rather than a tidiness concern, so it is
specified here, backed by `ON DELETE RESTRICT`, and covered by an integration test
asserting a renamed choice keeps its id.

### Concurrency

`@Version` on `TodoList` and `TodoItem`. Two mechanisms, both needed:

1. **An explicit precondition.** Every mutating request carries the `version` it was
   drawn from, and the service compares it to the loaded entity *before* mutating,
   throwing `StaleListException` / `StaleItemException`. This gives a deterministic
   409 for the common case - a stale browser tab - rather than one that depends on
   flush timing.
2. **The real race.** A genuine concurrent flush raises
   `ObjectOptimisticLockingFailureException`, mapped to the same 409.

The 409 body carries current state so the client reconciles in one round trip,
extending the existing `{error}` convention rather than replacing it. `code` is
present because the client must branch and prose is not a branch key:

```json
{ "error": "Ben completed this item while you were looking at it.",
  "code": "STALE_ITEM",
  "item": { "id": "...", "version": 7, "completedBy": {"displayName": "Ben"}, ... } }
```

The advice cannot re-read inside the failed transaction, so the exception carries
the id and the advice opens a fresh read-only one. If the entity is gone by then,
the response omits `item` and the client refetches the list.

### Exception to status

One advice per feature package, package-private,
`@RestControllerAdvice(assignableTypes = {...})`, returning `Map.of("error", ...)`
as `AdminExceptionHandler` does. Still no global advice and no `ProblemDetail`.

| Exception | Status |
|---|---|
| `ListNotFoundException`, `ItemNotFoundException` - also for "not readable" | 404 |
| `ListNotManagedException` | 403 |
| `ListNameTakenException`, `CostChoiceInUseException` | 409 |
| `StaleListException`, `StaleItemException` | 409 + `code` + state |
| `ObjectOptimisticLockingFailureException` | 409 + `code` + state |
| `InvalidFieldConfigurationException`, `MissingRequiredFieldException` | 400 |
| `UnknownMemberException`, `AssigneeWithoutAccessException` | 400 |
| `AdminPasswordProtectedException` | 409 |
| `UnsupportedImageTypeException` | 415 |
| `MaxUploadSizeExceededException` | 413 |
| `MethodArgumentNotValidException` | 400, body = first field message |

That last row is new behaviour and worth having: today a validation failure returns
Spring's default body, which the SPA's `errorMessage()` cannot read, so the UI shows
its generic fallback. With forms this large that is a visible loss of quality. It is
added to `AdminExceptionHandler` in the same change, so the two behave alike.

### Images and rich text

`io.minio:minio`, pinned, behind an `ImageStorage` interface. One bucket per
environment from an explicit `MINIO_BUCKET`, created by a `MinioBucketInitializer`
that fails startup when MinIO is unreachable. Bytes stream through
`GET /api/images/{id}` with `Cache-Control: private, max-age=86400` and an `ETag`
of the immutable id; presigned URLs are rejected because MinIO is not published
through the HTTPS front door and because a URL outlives a revoked grant. Type is
sniffed from the leading bytes, not the header or filename; PNG, JPEG and WebP only,
SVG refused by name. `max-file-size: 5MB`, and **`web/nginx.conf` needs
`client_max_body_size 6m` in `location /api/`** or nginx 413s the upload with an
HTML page the SPA cannot parse. Entitlement is purpose-dependent: `AVATAR` is
readable by anyone, including anonymously (adr/0019); `DETAIL` requires `readable`
on the owning list. See adr/0013.

Detail HTML is sanitized by `HtmlSanitizer` on every write, with `<img src>`
restricted to our own `/api/images/{uuid}` paths and no `style`, `class`, `on*`,
`<iframe>` or `<svg>`. The stored value is the sanitized value; the client renders
it directly and sanitizes nothing. The editor intercepts paste and drop to upload
rather than inline base64 - and if that is ever bypassed, the sanitizer strips the
image, so a bypass loses a picture instead of storing an attack. See adr/0018.

## Frontend

```
/login           LoginPage         restyled; the adr/0009 401-probe flow unchanged,
                                   rows now carry an avatar (adr/0019)
/lists           AllListsPage      search, filters, card grid
/lists/new       CreateListPage    a route, not a dialog: deep-linkable, and a
                                   three-step form is a bad dialog on a phone
/lists/:id       ListDetailPage    items; fires POST /touch on mount
/lists/:id/points  PointsTab       the per-list leaderboard
/lists/:id/edit  ListSettingsPage  owner only
/share           SharePage         pick a list -> create an item (adr/0020)
/admin           AdminPage         restyled master-detail + password reset
/profile         ProfilePage       avatar | password | appearance
```

Nested routes under a `ListDetailPage` layout route with MUI `Tabs` bound to the
pathname, not to tab state: deep-linkable, correct under the back button, and no
state to keep in sync. `ProtectedRoute` and `AdminRoute` keep their jobs.

`AppShell` replaces `AppLayout` and `SideNav` replaces `NavBar`, which is deleted
along with the bottom bar. `SideNav` top to bottom: the **List** wordmark; a
`PINNED` section, hidden entirely when empty because an empty labelled section is
noise; a `RECENT` section with a create button in its subheader and up to six
recently-touched lists, excluding pinned ones so no list appears twice; a
`MENU` section with *List* and, for an admin, *Users*; and a profile row pinned to
the bottom carrying the avatar, display name and sign out. Both list sections come
from one `GET /api/lists/sidebar` query, so the shell makes one request rather than
three. `AppShell` keeps `AppLayout`'s contract: it renders `null` until
`useCurrentUser()` resolves and sits inside `ProtectedRoute`.

One `Drawer`, `variant="permanent"` from the breakpoint up and `"temporary"` below,
with exactly one piece of layout state (`mobileNavOpen`) and exactly one
`useMediaQuery` call (adr/0015). The drawer closes on navigation.

Styling is the MUI theme (adr/0014): `theme.ts` seeded with today's palette,
`breakpoints.values.sm = 768` so `48rem` survives, `cssVariables` for both schemes,
and `index.css` cut to about forty lines of reset and safe-area helpers. No
component introduces a global class from here on.

Appearance is `LIGHT | DARK | SYSTEM`, with the server row authoritative and MUI's
`localStorage` mirror plus `InitColorSchemeScript` providing the first paint - the
mirror is what stops a dark-mode member seeing a light flash on every reload.

### The create flow

A `Stepper`, vertical on `xs` and horizontal from `sm`, all client state, one `POST`
at the end. **(1) Name it** - name, description, and an `IconPicker` of icon-only
`ToggleButton`s each carrying an `aria-label`, so tests and screen readers select by
accessible name. **(2) Who can see it** - an `Autocomplete multiple` over
`GET /api/users` rendering avatar-and-name chips, with the owner shown as a
non-removable chip and nobody else granted by default, since a private list is the
safe default and the "my personal workout list" case. **(3) What an item looks
like** - a locked *Description, always required* row, a switch and a Required
checkbox per optional field, and **Add a cost field** appending a named block with a
`FIXED`/`NUMERIC` toggle: either editable label-and-points rows seeded with
`5 min / 30 min / 60 min`, or a unit and a points-per-unit. Points sit behind one
"Earn points for finishing" switch per cost field, so a cost can exist without
points. `ListSettingsPage` reuses steps 1-3 as plain sections.

Curated icons are 20 tokens mirrored by a `ListIcon` enum server-side, so an unknown
token is a 400 from Jackson before any service runs. The frontend map falls back to
the default icon for a token it does not recognise, so the two lists drifting
degrades to a generic icon rather than a crash. `listIcons.tsx` holds one static map
with named imports; a namespace import or a dynamic string lookup would pull all
~11,000 icon modules into the bundle.

### Items and reconciliation

`ListDetailPage` renders items honouring the list's declared fields: a completion
checkbox, the description, and chips for assignee, due date and each cost value.
Tapping opens a detail drawer with the full form, built from the live configuration
so a field the list does not declare is never rendered.

`useToggleCompletion` and `useUpdateItem` optimistically update, then on a 409
carrying `item` write the server's state into the cache and raise a snackbar naming
the other member - "Ben completed this first" - rather than a modal, because the
member's intent already happened and there is nothing to decide. A 409 without
`item` invalidates and refetches. Anything else rolls back to the snapshot. Nothing
is silently retried, which is R30.

Query keys are a hierarchy so prefix invalidation reaches everything list-shaped:
`['lists']`, `['lists','sidebar']`, `['lists','search',filters]`,
`['lists','detail',id]` and `...,'items'|'access'|'fields'|'points'`. Every list
mutation invalidates `['lists']`; at this scale that is a handful of cheap requests,
and the alternative - a hand-maintained invalidation matrix - is where a stale
sidebar after a rename comes from. Pin/unpin optimistically updates the sidebar;
`POST /touch` invalidates only the sidebar, never the detail that fired it, or
opening a list would refetch itself. `refetchOnWindowFocus` stays `true` - that is
how another member's change arrives, and it is why recency must not live on a GET.

`api/client.ts` is split into `api/http.ts` plus a module per feature. It is not
left as a re-exporting barrel: the existing tests spy by module namespace, and a
re-exported live binding is exactly the case `vi.spyOn` cannot intercept, so the
four existing test files are repointed in the same change.

## Testing

- **Unit (backend)** - `CurrentUserProvider` throws for anonymous, a `String`
  principal and a null context; `LoginService`'s passwordless branch yields an
  `AppUserPrincipal` carrying the right id (the one test that catches the phase-1
  regression nothing else would); `readable`/`managed` for owner, grantee, stranger
  and **admin**, the admin getting nothing; the owner's implicit grant and its
  unremovability; name collision per owner but not across owners; a `FIXED` cost
  with no choices, a `NUMERIC` with no unit, two costs sharing a label, a merge
  preserving ids; the points maths - a fixed choice's own points, 1h at rate 1000 =
  1000, 2.5h = 2500, summed across two cost fields; completion crediting the
  completer and un-completion clearing all three columns; a stale version throwing
  before any mutation; magic-byte sniffing accepting PNG/JPEG/WebP and rejecting
  SVG and HTML renamed `.png`; the sanitizer against `<script>`, `onerror`,
  `javascript:`, an off-site `<img>` and `<svg>`, with ordinary formatting
  surviving; password set, clear, and refused for the admin.
- **Unit (frontend)** - the sidebar's three sections, Pinned absent when empty,
  Users only for an admin; the shell rendering nothing until the user resolves and
  the hamburger opening the drawer below the breakpoint (needs a `matchMedia` stub
  in `src/test/setup.ts`, which MUI requires and jsdom does not provide); the
  appearance preference driving the scheme and `SYSTEM` following the mocked media
  query; the create stepper, icon selection by accessible name, the owner chip
  being unremovable, and the final `POST` body; two cost fields configured, one
  fixed and one numeric; the item dialog rendering exactly the declared fields and
  blocking submit on a missing required one; the optimistic tick, a 409 with `item`
  replacing the row and naming the other member, a 409 without it refetching, and a
  500 rolling back; the points tab crediting the completer rather than the assignee;
  search and scope reaching the query; a too-large file refused before any request.
- **Integration** - against real Postgres, because this is where a predicate that
  filters in Java instead of in SQL passes every unit test: a stranger gets 404 on
  `GET`, `PATCH` and `DELETE` and the list never appears in their `GET /api/lists`;
  a grantee gets 200 then 403 on `PATCH`; the **admin** gets 404 on an ungranted
  list, which is the test that stops a future `|| isAdmin()`; revoking access turns
  a 200 into a 404 within one test; `POST` without CSRF is 403; the owner-name
  unique index yields 409 and not 500; every deferred position constraint survives
  reordering a three-field list in one transaction; a stale version on fields and on
  completion yields 409 carrying current state; `POST /touch` twice inside the
  window leaves one timestamp; a fixed cost round-trips labels, points and order,
  and a renamed choice keeps its id; cascades from deleting a list reach fields,
  choices, items, costs, grants and `list_user_state`; `GET /api/users` requires a
  session and carries no `role` or `passwordRequired`, while `GET /api/auth/users`
  still carries exactly id, username, displayName and `avatarImageId`;
  `PUT /api/me/password` succeeds for a member and 409s for the admin; a `DETAIL`
  image 404s without access while an `AVATAR` is readable anonymously; an SVG
  upload is 415. MinIO runs as a `MinIOContainer`, consistent with not mocking the
  data layer - the bucket-creation-at-startup path is exactly the configuration
  adr/0003 warned goes unexercised.
- **E2E** - new markers `lists`, `items`, `access`, `profile`, `theme` registered in
  `pyproject.toml`, since `--strict-markers` rejects unregistered ones. New
  fixtures: a `created_list` factory using `test_data.name()` with
  `defer_cleanup`, a `shared_list` granting the second member, and a `member_id`
  helper over the authenticated `GET /api/users`. The two-context scenarios are the
  reason this suite exists and nothing has used `second_member_page` for its
  purpose until now: **access is real** - a list granted to nobody is absent from
  the other member's list page, unfindable by exact name, and not-found by direct
  URL, then appears when granted and vanishes when revoked; **concurrent
  completion** - both members open a shared item, one ticks it, the other ticks it
  and is told, by name, with the row reconciling to the truth and the points landing
  once; **assignment is not attribution** - Ana assigns to Ben, Ana completes, Ana
  earns, which is the rule a refactor is most likely to invert and is only
  observable end to end; a shared link becoming an item visible to the other
  member; the field configuration surviving a reload; the drawer on a 390px
  viewport, this being the one place the design admits layout state. Password tests
  use a disposable account created through `admin_api`, never seeded Ana or Ben - a
  test that leaves Ben with a password breaks every other test's sign-in, and that
  trap is named in the module docstring.

`e2e/conftest.py`'s `ui_sign_in` waits on `heading "Lists"`, and every test depends
on it. `AllListsPage` keeps that `<h1>`, or `conftest.py` changes in the same phase.
