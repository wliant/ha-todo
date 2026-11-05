# 0006 - Data lists and entry search

Implements [requirements/0006](../requirements/0006-data-lists-and-entry-search.md)
R50-R61. Decisions recorded in
[adr/0029](../adr/0029-a-data-list-is-its-own-aggregate.md),
[adr/0030](../adr/0030-an-entrys-searchable-document.md) (superseded by
[adr/0033](../adr/0033-tags-enter-the-search-document-stemmed.md)),
[adr/0031](../adr/0031-entry-search-is-one-statement-with-a-trigram-fallback.md)
and [adr/0032](../adr/0032-a-detail-pictures-owner-is-a-third-foreign-key.md).

## Scope

A **data list** is a reference collection and a sibling of the todo list, not a
mode of it (adr/0029). Its own tables, its own package, its own authorization
code - which is a deliberate copy of the todo list's, because the duplication is
what keeps a recipe out of the dashboard.

Almost everything else is reuse. The grant model, the optimistic-locking
discipline, the HTML sanitizer, the claimed-image pipeline and the detail editor
all transfer unchanged. The one genuinely new piece of engineering is search,
and it is the last thing built, because it wants a stable schema underneath it.

Build in this order. Each step leaves the tree green.

1. **Migrations** V912-V915.
2. **`com.hatodo.richtext` extractions** - `HtmlText` and `DetailImages`, with
   `TodoItemService` switched onto the latter so the image-reference regex has
   one home rather than two.
3. **`DataList`** - entity, access, repository, service, controller, advice.
   This is the pattern copy, and it is fully tested before anything depends on
   it.
4. **`DataEntry`** - entity, repository, service, controller, advice.
5. **`DataEntrySearchQuery`** - the native statement.
6. **Image wiring** - `DataEntryImageAccess` and `ImageController`'s three-way
   decision.
7. **Frontend** - API modules, queries, pages, nav entry.
8. **The share picker** - last, because it needs both features present.

Out of scope, from R50's exclusions: no assignment, no due date, no completion,
no points, no recurrence, no dashboard participation, no pins, no recency, no
per-collection field configuration, no manual ordering.

## Data

Four migrations rather than one. `CREATE EXTENSION` is the only statement in the
set needing more than table ownership, and it failing on its own says something
useful; failing halfway through a table definition does not.

### V912 - the extension

`CREATE EXTENSION IF NOT EXISTS pg_trgm`. A *trusted* extension since Postgres
13, so the owner of a database may install it without superuser - which is what
`hatodo_dev` and `hatodo_prod` are. Needed for R55's substring arm
(adr/0031).

### V913 - collections and their grants

`data_list` carries `id`, `owner_id` (not transferable, as on `todo_list`),
`name`, `description`, `icon`, `version`, `created_at`, `updated_at`, and a
`btrim(name) <> ''` check. `ux_data_list_owner_name` is unique on
`(owner_id, lower(name))` - a **separate namespace** from todo lists, so one
member may keep both a "Restaurants" collection and a "Restaurants" todo list.

`data_list_access` is `(data_list_id, user_id)` as the primary key, plus
`granted_at` and `granted_by`, with `ix_data_list_access_user` on `user_id`
because "which collections can I see" begins every read including global search.
No role column: a grant is binary, as in 0004.

`icon` draws from the existing `ListIcon` set and adds no values - it is a
closed set of drawings with no behaviour, and the frontend mirror in
`listIcons.tsx` is one file.

### V914 - entries

`data_entry` carries `id`, `data_list_id`, `name`, `description`, `detail_html`,
`detail_text`, `tags text[]`, `tags_text`, `version`, `created_by`,
`created_at`, `updated_at`.

The invariants, and who holds each:

| Invariant | Held by |
| --- | --- |
| Name present and not blank | `CHECK (btrim(name) <> '')` and `@NotBlank` |
| Detail HTML sanitized, images only `/api/images/{uuid}` | `HtmlSanitizer`, on write (adr/0018) |
| `detail_text` is `detail_html` with the markup taken out | `DataEntry.setDetail(html, text)` - the only writer of either |
| `tags_text` is `tags` joined by a space | `DataEntry.setTags(tags)` - the only writer of either |
| Tags lowercase, trimmed, deduped, sorted | `TagNormaliser` - SQL cannot say it without a trigger |
| No NULL tag element; at most twenty tags | `CHECK` constraints |
| `search_vector` reflects all four sources, weighted | the generated column itself; `aTagHitOutranksADetailHit` proves the weights are live |
| An entry belongs to a collection the writer can read | `DataListService.requireReadable` |

Then the search document, per adr/0033:

```sql
ALTER TABLE data_entry ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')),        'A')
     || setweight(to_tsvector('english', coalesce(tags_text, '')),   'B')
     || setweight(to_tsvector('english', coalesce(description, '')), 'C')
     || setweight(to_tsvector('english', coalesce(detail_text, '')), 'D')
    ) STORED;
```

Tags are stemmed into the document through `tags_text` rather than entered
verbatim by `array_to_tsvector`, because `setweight` cannot weight a
position-less lexeme and an unstemmed tag cannot be matched by a member typing
it - both measured against a real Postgres rather than assumed. adr/0033 has the
numbers. The `tags` array itself is untouched and is still what R56's chips
filter with `@>`, exactly.

with four indexes: GIN on `search_vector`, GIN on `tags` (for `@>` filtering and
the suggestion endpoint's `unnest`), GIN on `lower(name) gin_trgm_ops` (the
substring arm), and a plain btree on `(data_list_id, lower(name))` for listing a
collection in display order.

**Three traps, named because each one fails late.**

*A generation expression must be `IMMUTABLE`.* The explicit-config
`to_tsvector('english', …)` is; the one-argument form is only `STABLE` and is
refused. So are `array_to_string(tags, ' ')` and the `tags::text` cast, both for
the same reason - an element's output function may depend on session settings -
which is why the tags reach the vector through a column the application writes
rather than through an expression over the array. Verified on Postgres 17:
`array_to_string` and `to_tsvector(text)` report `provolatile = 's'`, and
attempting the generated column with either is refused outright with
"generation expression is not immutable".

*A generated column that Postgres accepts is not a generated column that works.*
`setweight(array_to_tsvector(tags), 'B')` creates, indexes and queries without a
complaint, and is inert - adr/0033. Probe the expression against a real database
before trusting it; a test written against the schema as described would have
agreed with the description.

*`detail_text` is app-maintained, not generated.* The only HTML-stripping
expression available inside a generated column is a regexp, and
`regexp_replace(detail_html, '<[^>]*>', '', 'g')` turns
`<li>milk</li><li>sugar</li>` into `milksugar`. Element boundaries must become
spaces and entities must be decoded, which is a parser.

*`search_vector` is not mapped in JPA at all.* Hibernate has no `tsvector` type;
`ddl-auto: validate` ignores a column nobody claims. `tags` must be
`@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") String[]` -
a `List<String>` is either an element collection or a serialised blob, and
validation will say so at startup.

### V915 - entry pictures, and two repairs

`stored_image` gains `entry_id` referencing `data_entry` with
`ON DELETE CASCADE`, plus `ix_stored_image_entry` partial on
`entry_id IS NOT NULL` (adr/0032).

Two existing objects are corrected in the same migration, because this is the
change that makes both matter.

`ck_stored_image_owner` is currently `item_id IS NULL OR rule_id IS NULL`, a
form that does not generalise. Postgres cannot alter a `CHECK`, so it is dropped
and recreated as `num_nonnulls(item_id, rule_id, entry_id) <= 1`, which will not
need rewriting again.

`ix_stored_image_unclaimed` is currently
`WHERE item_id IS NULL AND purpose = 'DETAIL'` - written in V907, never updated
when `rule_id` arrived in V911, and therefore **already reporting every
rule-owned picture as an unclaimed orphan**. Nothing sweeps orphans today
(adr/0013), so it is misleading rather than destructive, but a sweeper written
against it would delete the pictures behind every recurrence rule. Recreated
covering all three owner columns.

### Migration path

Nothing to backfill: every table is new, and `stored_image.entry_id` starts
null everywhere, which is the correct value for every existing row. The two
corrections are index and constraint replacements on a table whose data does not
change. Local and dev may be dropped and rebuilt; prod applies V912-V915 in
order with no data migration step.

No seed data. `db/seed` stays as it is - demo collections would be one more
thing to keep true, and the feature is demonstrable by creating one.

## Backend

New package `com.hatodo.data`, with unit tests mirroring it under `src/test`.

```
com.hatodo.data
  DataList, DataListAccess, DataListAccessId
  DataListRepository, DataListAccessRepository
  DataListService              authorization lives here and nowhere else
  DataListController           @RequestMapping("/api/data-lists")
  DataListExceptionHandler     @RestControllerAdvice(assignableTypes = DataListController.class)
  CreateDataListRequest, UpdateDataListRequest, UpdateDataListAccessRequest
  DataListSummaryResponse, DataListDetailResponse, DataListMemberResponse
  DataListNotFoundException, DataListNotManagedException, DataListNameTakenException
  StaleDataListException, DataListDeletedEvent

  DataEntry, DataEntryRepository
  DataEntrySearchQuery         native SQL; the projection and tag suggestions
  DataEntryService
  DataEntryController          @RequestMapping("/api")
  DataEntryExceptionHandler
  DataEntryImageAccess         @Component implements images.DataEntryImageAccess
  SaveDataEntryRequest, DataEntryResponse, DataEntryHit, TagSuggestionResponse
  DataEntryNotFoundException, StaleDataEntryException, InvalidTagException
  TagNormaliser                pure
  DataSearchQueryText          pure
```

Two helpers move into `com.hatodo.richtext` so each rule has one
implementation:

- **`HtmlText.toPlainText(String html)`** - tags become spaces, entities are
  decoded, whitespace collapses, blank becomes null. `HtmlSanitizer`'s private
  tag-stripping stays where it is: it answers "did anything survive sanitizing",
  a different question with a lower correctness bar. This one feeds an index.
- **`DetailImages.referencedIn(String... html)`** - the `/api/images/{uuid}`
  scan that is today a private constant inside `TodoItemService`. Extract it and
  switch `TodoItemService` onto it in the same change, or there are two copies
  of the rule that makes adr/0018's `img[src]` restriction meaningful.

### Authorization

`DataListService` holds both predicates and is the only place either appears -
not the controller, not the UI. They are `TodoListService`'s, verbatim in
behaviour:

- `readable` - the caller owns the collection, or holds a `data_list_access`
  row. Enforced in SQL by an `EXISTS` join in `findVisible` / `findVisibleById`,
  never by fetching and filtering in Java.
- `managed` - the caller owns it. Deliberately **not** `owner || isAdmin()`: the
  admin administers accounts, not collection contents (adr/0010).

No grant answers **404**, because a collection the caller holds no grant on must
not be discoverable. A grant without ownership answers **403** on the
collection's own settings and its access list.

```java
DataList requireReadable(UUID listId);                       // throws → 404
boolean  isReadable(UUID listId);                            // no throw
DataList requireManaged(UUID listId);                        // throws → 403
DataList requireManagedAndFresh(UUID listId, Long version);  // throws → 403 or 409
void     markChanged(DataList list);
```

`isReadable` is not an optimisation. Catching the exception `requireReadable`
raises does not undo the rollback-only mark on the surrounding transaction, and
the commit then fails with a 500 where a 404 was the point. `DataEntryImageAccess`
must use it.

`replaceMembers` calls `markChanged` - `LockModeType.OPTIMISTIC_FORCE_INCREMENT` -
for the reason `TodoListService.replaceMembers` does: Hibernate will not
dirty-check a parent whose own columns are untouched, so a second editor would
never be told. Unlike the todo version there is no revocation event to publish:
entries are never assigned, so a revoked grant has no consequence beyond the
collection becoming invisible.

**Entry writes check `readable`, never `managed`** - R60. Anybody who can see a
collection is a peer on its entries, which is `TodoItemService`'s rule verbatim
(item deletion is readable-gated there too).

### The write path

`create` and `update` on `DataEntryService`, in this order:

```java
lists.requireReadable(listId);                       // 404 if no grant
requireFreshView(entry, request.version());          // 409 before anything mutates
String html = sanitizer.sanitize(request.detailHtml());
entry.setDetail(html, htmlText.toPlainText(html));   // one setter, both columns
entry.setTags(tagNormaliser.normalise(request.tagsOrEmpty()));
claimImages(entry, currentUser.requireUserId());
```

The version is checked **before** anything is mutated, so a 409 is deterministic
rather than dependent on flush timing - the discipline items already follow.
`isFreshView` exists alongside `requireFreshView` for the same no-throw reason
`isReadable` does.

`DataEntry` exposes no `setDetailHtml` and no `setDetailText`. One setter taking
both means the two columns cannot be made to disagree by any caller, present or
future (adr/0030).

`claimImages` is `TodoItemService.claimImages` with the owner swapped: only
pictures this member uploaded, only ones with no owner at all, or already this
entry's. An image id alone must never drag somebody else's picture onto a
collection.

Entries are matched by id on update. There is no composite-identity child
collection here, so `item_cost_value`'s `NonUniqueObjectException` trap does not
apply - and would, the moment somebody moved tags to a table keyed
`(entry_id, tag)`, which is a second reason the array wins.

### Search

One native statement in `DataEntrySearchQuery`, on `NamedParameterJdbcTemplate`,
serving both routes with `:listId` null or not (adr/0031). Precedent for
dropping out of JPA: `StoredImageRepository.findForList`.

```sql
WITH q AS (
    SELECT CASE WHEN CAST(:tsquery AS text) IS NULL THEN NULL
                ELSE to_tsquery('english', CAST(:tsquery AS text)) END AS tsq
)
SELECT e.id, e.data_list_id, l.name AS list_name, l.icon AS list_icon,
       e.name, e.description, e.tags, e.version, e.updated_at,
       CASE WHEN q.tsq IS NOT NULL AND e.search_vector @@ q.tsq
            THEN ts_rank('{0.1,0.25,0.5,1.0}'::float4[], e.search_vector, q.tsq, 32)
            ELSE 0 END AS rank,
       CASE WHEN CAST(:needle AS text) IS NULL THEN 0
            ELSE similarity(lower(e.name), CAST(:needle AS text)) END AS name_similarity
FROM data_entry e
JOIN data_list l ON l.id = e.data_list_id
CROSS JOIN q
WHERE EXISTS (SELECT 1 FROM data_list_access a
              WHERE a.data_list_id = l.id AND a.user_id = CAST(:userId AS uuid))
  AND (CAST(:listId AS uuid) IS NULL OR e.data_list_id = CAST(:listId AS uuid))
  AND (CAST(:tags AS text[]) IS NULL OR e.tags @> CAST(:tags AS text[]))
  AND ( (CAST(:tsquery AS text) IS NULL AND CAST(:needle AS text) IS NULL)
     OR (q.tsq IS NOT NULL AND e.search_vector @@ q.tsq)
     OR (CAST(:needle AS text) IS NOT NULL
         AND (lower(e.name) LIKE CAST(:likePattern AS text) ESCAPE '\'
              OR EXISTS (SELECT 1 FROM unnest(e.tags) t
                         WHERE t LIKE CAST(:likePattern AS text) ESCAPE '\'))) )
ORDER BY rank DESC, name_similarity DESC, lower(e.name) ASC, e.id
LIMIT :limit
```

Every clause that is easy to get wrong:

- **The grant `EXISTS` is in the same `WHERE` as the `@@`**, a semi-join against
  `ix_data_list_access_user`. The planner may BitmapAnd the GIN hits with the
  grant-restricted set or drive from grants on a small household; either is
  correct, and no code path can bypass it. Revoking a grant needs no cache
  invalidation.
- **Every nullable parameter is cast.** Without `CAST(:listId AS uuid)`,
  Postgres answers "could not determine data type of parameter $N" the first
  time it is null - which is the global path, the main one. This is the single
  most likely reason this query does not ship first time.
- **The CTE is inlined**, not materialised: non-recursive, side-effect-free,
  referenced once. It exists only so the `tsquery` is parsed once.
- **`rank` is the primary sort and substring-only hits rank 0**, so a fuzzy match
  can never displace an exact one; `name_similarity` orders within the fallback
  band. Normalisation `32` is `rank/(rank+1)`, which keeps the score in `[0,1)`
  so a long detail cannot win on volume.
- **Tag filtering is `@>`** - AND semantics, narrowing, per R56. Say so in the
  response type's javadoc: "thai and quick" is what a chip row means to a
  member.

`DataSearchQueryText` is pure, so the mode decision is unit-testable without a
database:

- Default: `plainto_tsquery('english', raw)`, then promote **only the last
  lexeme** by rewriting the trailing `'` of its text form to `':*`.
  `plainto_tsquery` has already stemmed, dropped stopwords and neutralised every
  operator character, so its output is nothing but quoted lexemes joined by `&` -
  which is what makes a textual rewrite safe. `nullif(…, '')` on an
  all-stopword query yields NULL, which falls through to the substring arm
  rather than erroring.
- Quotes or a leading `-` in the raw query switch to
  `websearch_to_tsquery('english', raw)` with **no** prefix promotion: a prefix
  inside a phrase silently changes the phrase's meaning, and a member who typed
  quotes asked for precision.
- The substring needle is non-null only for a **single token of at least three
  characters**. When it is null the fallback arm folds to a constant and the
  planner drops it.
- The LIKE pattern is `'%' + escaped + '%'`, where `escaped` doubles `\` and
  escapes `%` and `_`. An unescaped `%` turns a member's search into "match
  everything".

Tag suggestions (R52) are a second small statement in the same class:
`unnest(tags)` over the collection's entries, `GROUP BY`, count descending, with
an optional prefix. Grant-checked through `DataListService.requireReadable`
before it runs.

### REST

```
GET    /api/data-lists                   ?q=&icon=&scope=all|owned|shared
POST   /api/data-lists
GET    /api/data-lists/{id}
PATCH  /api/data-lists/{id}
DELETE /api/data-lists/{id}
GET    /api/data-lists/{id}/access
PUT    /api/data-lists/{id}/access
GET    /api/data-lists/{id}/tags         ?q=              suggestions, with counts
GET    /api/data-lists/{id}/entries      ?q=&tag=&tag=    scoped search, or a plain listing
POST   /api/data-lists/{id}/entries
GET    /api/data-entries                 ?q=&tag=&limit=  global search (R54)
GET    /api/data-entries/{id}
PATCH  /api/data-entries/{id}
DELETE /api/data-entries/{id}
```

`DataEntryController` is mapped at `/api` and holds both path families, which is
`ItemController`'s shape and keeps one `@RestControllerAdvice` per controller.
Scoped and global search are the same handler with `listId` null or not.

`DataEntryHit` carries `dataListId`, `listName` and `listIcon`, so a global
result says which collection it came from without an N+1 (R54).

Errors follow the house style. `{"error": …}` for 400 and 404, with **the same
body** for "gone" and "no grant". 403 for a grant-holder trying to manage a
collection. 409 as
`{"code": "STALE_DATA_LIST" | "STALE_DATA_ENTRY", "error": …, "dataList" | "entry": <current>}`,
with the current state re-read in a **fresh** transaction because the one that
threw is rolling back. `DataIntegrityViolationException` maps to 409 for the
owner/name unique index.

`SaveDataEntryRequest` uses boxed, nullable components with a defaulting
accessor, because Jackson rejects a missing primitive in a record - and because
in a request that replaces a set, "not mentioned" is genuinely different from
"empty":

```java
public record SaveDataEntryRequest(
        Long version,                                   // null when creating
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        String detailHtml,
        List<String> tags) {
    public List<String> tagsOrEmpty() { return tags == null ? List.of() : tags; }
}
```

There is no `position` column and no ordering parameter: R57 orders by name, and
adding manual ordering later is additive.

### Images

`stored_image.entry_id`, per adr/0032, and a **second** single-method interface
in the `images` package rather than a method on `ImageAccess`:

```java
public interface DataEntryImageAccess { boolean canReadEntry(UUID entryId); }
```

`ImageController` injects `ImageAccess` by type and `ItemImageAccess` is its only
implementation - a second `@Component implements ImageAccess` is a
`NoUniqueBeanDefinitionException` at startup, not a compile error. And a method
added to `ImageAccess` would have to be implemented in `com.hatodo.item`, which
must not learn about collections.

`mayRead` becomes a decision on which foreign key is set:

```java
if (image.getPurpose() == ImagePurpose.AVATAR) return true;   // adr/0019
if (image.getItemId()  != null) return itemAccess.canReadItem(image.getItemId());
if (image.getEntryId() != null) return entryAccess.canReadEntry(image.getEntryId());
return false;   // rule-owned and unclaimed both answer no, as today
```

No new `ImagePurpose` value: an entry's picture asks exactly the question an
item's does. `ImageUploadController` needs no change at all.

### Concurrency

Both `DataList` and `DataEntry` carry `@Version`, and every mutating request
carries the version its view was drawn from. Two members editing one entry is
R60's case: the second gets a 409 carrying the entry as it actually is, the
client takes the server's copy outright and raises a snackbar, and nothing is
retried behind anybody's back. Grants and the collection's own fields are
version-checked through `requireManagedAndFresh`.

## Frontend

### Routing and navigation

Routes inside the existing `AppShell` nesting in `App.tsx`: `/collections`,
`/collections/new`, `/collections/:listId`, `/collections/:listId/settings`.

One entry in `SideNav`'s **Menu** section, below "Lists" - R58. No second
navigation surface, and the sidebar's existing Pinned and Recent sections are
untouched, because collections have neither (adr/0029).

`AppShell` keeps the only piece of layout state and the only `useMediaQuery`
call in the app (adr/0015). Nothing here acquires either. Everything responsive
stays CSS, and no component introduces a global class - `sx`, a styled
component, or a theme override, per adr/0014.

### API modules and queries

`web/src/api/dataLists.ts` and `web/src/api/dataEntries.ts`, each holding its own
DTOs and calls over the shared axios instance in `http.ts`. **No barrel** - tests
spy by module namespace, and a re-exported live binding is exactly what
`vi.spyOn` cannot intercept.

`web/src/data/queries.ts` mirrors `lists/queries.ts`'s key convention:

```ts
dataListKeys = { all: ['dataLists'], search: (f) => [...], detail: (id) => [...],
                 tags: (id) => [...], entries: (id, f) => [...] }
dataEntryKeys = { search: (f) => ['dataEntries', 'search', f] }
```

Mutations invalidate by the `all` prefix. The global search query is keyed on its
filters and debounced in the component, not in the key.

### Screens

- **`CollectionsPage`** - the index, mirroring `AllListsPage`: the collections
  the member can see, with the global entry search (R54) above them. A hit shows
  its collection's name and icon and navigates into that collection's entry.
- **`CollectionDetailPage`** - the entries, with a search field scoped to this
  collection and a filter chip row built from `GET .../tags` (R56). Entry rows
  expand one at a time, reusing the pattern items already use for R38: the
  expanded id is one piece of component state, and expanding one collapses the
  other by construction rather than by an effect.
- **`EntryForm`** - name, description, tags, detail. The detail uses the existing
  `web/src/items/DetailEditor.tsx` unchanged - it is already generic over
  `label` and `helperText` and already serves both the item detail and the
  completion note. Keep the `lazy(() => import(...))` plus `<Suspense>` wrapper:
  ProseMirror is ~130KB gzipped, and 0006's consequence 7 accepts that a
  collection pays it nearly always.
- **`CollectionSettingsPage`** - name, description, icon, and the access list,
  mirroring `ListSettingsPage`. Owner only; a grant-holder who reaches it sees
  the 403 as a message, not a blank screen.

Stored detail HTML is rendered with `dangerouslySetInnerHTML` and the client
sanitizes **nothing** - a second implementation of adr/0018's rule would drift
from the first.

### The share picker

`SharePage` currently builds `choices` from `useSidebar()`'s `recent` and
`pinned`. For R59 it gains a second query for collections and groups the picker
under two subheadings - "Lists" and "Collections" - so the difference 0006's
consequence 1 warns about is legible without a sentence of explanation.

Two calls merged in the view model, **not** a combined endpoint: two feature
modules, two endpoints. Shared title becomes the entry's name; the link goes
into the detail as an `<a href>`, which `HtmlSanitizer` already permits with
`rel=nofollow` forced, so no backend change is needed for `/share` itself.

Because collections have no recency, the "most recently touched is preselected"
behaviour stays todo-only and collections sort by name. That is 0006's
consequence 5 and adr/0029's stated gap - documented, not overlooked. The fix,
if it is ever wanted, is a `data_list_user_state` table mirroring
`list_user_state`; **not** a widened foreign key on the existing one.

## Testing

### Unit - `app/src/test/java/com/hatodo/...`, mirroring packages

`richtext/HtmlTextTest` - `replacesBlockBoundariesWithSpaceSoWordsDoNotMerge`
(`<li>milk</li><li>sugar</li>` yields `milk sugar`, the whole reason the class
exists), `decodesEntities`, `dropsMarkupButKeepsLinkText`,
`returnsNullWhenNothingIsLeft`.

`richtext/DetailImagesTest` - `findsEveryOwnImageReference`,
`ignoresForeignUrls`, `deduplicates`.

`data/TagNormaliserTest` - `lowercasesAndTrims`, `deduplicates`, `sorts`,
`rejectsBlankTag`, `rejectsOverLongTag`, `rejectsMoreThanTwentyTags`.

`data/DataSearchQueryTextTest` - `promotesOnlyTheLastLexemeToAPrefix`,
`choosesWebsearchForAQuotedPhrase`, `choosesWebsearchForANegatedTerm`,
`noPrefixPromotionInAdvancedMode`, `escapesLikeWildcards`,
`noNeedleForATwoCharacterQuery`, `noNeedleForAMultiTokenQuery`,
`blankQueryProducesNeitherTsqueryNorNeedle`.

`data/DataListServiceTest` - `aCollectionWithoutAGrantIsNotFound`,
`aGrantHolderCannotManageIt`, `createWritesTheOwnersOwnGrant`,
**`createDoesNotWriteAnyListFields`** (the regression guard against adr/0029's
rejected design creeping back), `refusesADuplicateNamePerOwner`,
`refusesAStaleUpdate`, `replaceMembersKeepsTheOwner`,
`replaceMembersMovesTheVersion`.

`data/DataEntryServiceTest` - `sanitizesDetailOnWrite`,
`writesDetailTextFromTheSanitizedHtml`, `normalisesTagsBeforeStoring`,
`refusesAStaleEdit`, `refusesAnEntryOnAnInvisibleCollection`,
`claimsOnlyUnclaimedPicturesTheCallerUploaded`, `doesNotClaimAnItemsPicture`,
`deleteForgetsThePictures`.

`data/DataEntryTest` - `settingDetailWritesBothColumns`,
`settingTagsWritesBothColumns`, `thereIsNoWayToWriteDetailTextAlone` and
`thereIsNoWayToWriteTagsTextAlone`: cheap, and they are the invariant the whole
search design rests on.

### Integration - `app/src/integrationTest/java/com/hatodo/`

Flat, as the existing ones are, with `app.recurrence.enabled: false` already set
in the itest profile - a Testcontainers run can otherwise fire the recurrence
job mid-assertion.

`DataListApiIntegrationTest` - `creatingACollectionGrantsTheOwnerAccess`,
`aCollectionWithoutAGrantAnswers404`,
`aGrantHolderEditingTheCollectionAnswers403`,
`aDuplicateNamePerOwnerAnswers409`,
`twoOwnersMayEachKeepARecipesCollection`,
`aCollectionAndATodoListMayShareAName`,
`aStaleUpdateAnswers409WithTheCurrentCollection`,
`revokingAGrantHidesTheEntriesToo`,
`deletingACollectionRemovesItsEntriesAndTheirImageRows`.

`DataEntryApiIntegrationTest` - `anyGrantHolderMayAddEditAndDeleteAnEntry`,
`detailHtmlIsSanitizedOnWrite`, `detailTextTracksDetailHtml` (asserted on the
column directly, including that a word boundary survives),
`tagsTextTracksTheTagsArray`, `tagsAreStoredLowercasedDedupedAndSorted`,
`tagSuggestionsComeFromTheCollectionAndCarryCounts`,
`aStaleEditAnswers409WithTheCurrentEntry`,
`anEntryOnAnInvisibleCollectionAnswers404`.

`DataEntrySearchApiIntegrationTest` - the suite's real reason to exist, because
none of adr/0031's SQL has a unit-testable form. `scopedSearchMatchesName`,
`…MatchesDescription`, `…MatchesTheDetailsText`, `…MatchesATag`,
`theGeneratedVectorIsRewrittenWhenAnEntryIsEdited`,
`globalSearchReportsWhichCollectionEachHitCameFrom`,
`globalSearchSkipsCollectionsTheCallerHoldsNoGrantOn`,
`prefixMatchingFindsAvianFromAvi`, `substringFallbackFindsAvianFromVian`,
`aFullTextHitOutranksASubstringHit`, `aNameHitOutranksADetailHit`,
`aTagHitOutranksADetailHit` (adr/0033's regression guard - the superseded form
ranked these equal), `aTagIsFoundByItsSingularAndItsPlural`,
`aQuotedPhraseMatchesOnlyThePhrase`, `aNegatedTermExcludes`,
`aStopwordOnlyQueryReturnsNothingRatherThanFailing`,
`aPercentSignInTheQueryIsNotAWildcard`, `tagFiltersNarrowRatherThanWiden`,
`theResultSetIsBounded`. Ranking is asserted as order, not membership.

`DataEntryImageApiIntegrationTest` - `anEntryPictureIsReadableByEveryGrantHolder`,
`anEntryPictureIsNotReadableWithoutAGrant` (404, not 403),
`anUnclaimedPictureIsReadableOnlyByWhoeverUploadedIt`,
`aMemberCannotClaimSomebodyElsesPicture`, `deletingAnEntryRemovesItsPictures`,
`anItemPictureCannotBeClaimedByAnEntry` - the `num_nonnulls` check, end to end.

### Frontend

Co-located `*.test.tsx`: `CollectionsPage.test.tsx`,
`CollectionDetailPage.test.tsx`, `EntryForm.test.tsx`, `TagInput.test.tsx`,
`EntryRow.test.tsx` (one expanded at a time), and a `SharePage.test.tsx` case
for the two-group picker. `DetailEditor` is unchanged and its existing test
stands.

### E2E

`e2e/tests/test_data_lists.py`, against dev. Label `test_data.name()` strings per
**module**, not per concept - the fixture is session-scoped, so two modules both
asking for `name("Recipes")` get the same string and the second create fails on
the unique index. Cases: a member creates a collection and adds an entry with a
bulleted detail; a second member with a grant edits that entry; global search
finds an entry by a word only present in its detail and says which collection;
a member without a grant gets nothing from global search; and - the suite's
reason to exist - two browser contexts editing one entry, where the second sees
the conflict and the server's copy rather than a silent overwrite.
