# 0031 - Entry search is one native statement with a trigram fallback

Status: accepted
Date: 2026-11-05

## Context

0006 asks for four things from one search box. It reads the whole entry (R53).
It works across every collection the caller can see, saying which collection
each hit came from (R54). It works scoped to one collection. And it finds a word
by its middle, not only by its start - "avi" and "vian" both find "avian" (R55).
Tag filters compose with all of it (R56).

Two of those are not expressible in JPQL. `tsvector`, `tsquery`, `@@`, `ts_rank`,
`websearch_to_tsquery`, `array_to_tsvector` and `similarity()` have no JPQL
equivalent, and `tsvector` has no Hibernate basic type - mapping the column to
make `FUNCTION('...')` work would also put it in front of `ddl-auto: validate`,
which has an opinion about types it does not recognise.

Prefix matching is half-free. GIN on a `tsvector` supports a prefix lexeme
natively, so promoting the last word a member typed to `avi:*` finds `avian` at
no cost. `vian` finding `avian` is a different question that full text cannot
answer at all - a lexeme either matches or it does not - and 0006 R55 names the
vocabulary collection specifically because a word you cannot find by its middle
is a word you cannot look up.

And whatever shape the query takes, the access rule from adr/0010 still holds:
filtering happens in SQL by joining the grant table, never by fetching rows and
filtering in Java, and a collection id in a request is never proof of access.

## Decision

**One native SQL statement**, in a hand-written `DataEntrySearchQuery` on
`NamedParameterJdbcTemplate`, serving both the global and the scoped route.
`:listId` null means global; non-null means scoped. There is no second
implementation to drift.

Four things about that statement are load-bearing.

**The grant check is the driving predicate**, not a filter applied afterwards:
`EXISTS (SELECT 1 FROM data_list_access a WHERE a.data_list_id = l.id AND
a.user_id = :userId)` sits in the same `WHERE` as the `@@`. There is no code path
that can fetch and then filter, and revoking a grant takes effect on the next
query with nothing to invalidate.

**Every nullable parameter is cast** - `CAST(:listId AS uuid)`,
`CAST(:tags AS text[])`. Without the cast Postgres answers "could not determine
data type of parameter $N" the first time a parameter is null, which is exactly
the global-search path.

**Ranking is `ts_rank` with the weights `{0.1, 0.25, 0.5, 1.0}` and
normalisation `32`** (`rank/(rank+1)`), so a long detail cannot outrank a short
name by sheer volume, and adr/0030's A-B-C-D weighting actually shows up in the
order.

**The query mode is decided in Java**, by a pure `DataSearchQueryText` with no
database behind it:

- By default, `plainto_tsquery` normalises the member's words, and the **last
  lexeme only** is promoted to a prefix by appending `:*` to the trailing quote
  of its text form. `plainto_tsquery` has already stemmed, dropped stopwords and
  neutralised every operator character, so its output contains nothing but
  quoted lexemes and `&` - which is what makes the promotion safe to do by
  rewriting text. A stopword-only query produces an empty `tsquery`, which
  `nullif` turns into NULL, which falls through to the substring arm instead of
  erroring.
- The moment the member uses quotes or a leading `-`, `websearch_to_tsquery`
  takes over and **nothing is promoted to a prefix**. Someone who typed quotes
  asked for precision, and a prefix inside a phrase query silently changes the
  phrase's meaning.

**Substring matching is a fallback arm**, under three rules that keep it from
taking over the statement:

1. It ranks **0**, so a fuzzy hit can only ever appear *below* every genuine
   full-text hit. A member never sees an approximate match displace an exact one.
2. It applies to `name` and `tags` only - short, label-like fields - and never to
   the description or the detail body. A `LIKE '%...%'` over every recipe body
   is a different feature with a different cost, and nobody asked for it. This is
   also why there is one trigram index, on `lower(name)`, rather than four.
3. It is gated in Java to a **single token of at least three characters**. A
   trigram index cannot help a one- or two-character needle, and a single letter
   would match everything. When the needle is null the whole arm folds to a
   constant and the planner drops it.

`pg_trgm` is installed by its own migration, because it is the only statement in
this feature needing more than table ownership and a clear failure is worth more
than a terse one halfway through a table definition.

## Consequences

Both routes are one statement, so scoped and global search cannot disagree about
what matches or how it ranks, and the `EXISTS` makes it structurally impossible
to leak an entry from a collection the caller holds no grant on. The
`DataEntrySearchApiIntegrationTest` suite is the only place any of this can be
tested - none of the SQL has a unit-testable form - which is why it is the
largest of the three integration tests and asserts ranking order explicitly, not
just membership.

Going native costs the things Hibernate was doing: no automatic type mapping, a
column list maintained by hand, and no flush of pending writes before the
statement runs - so search must never be called in the same transaction as an
unflushed write and expected to see it. Search runs `@Transactional(readOnly =
true)`, so that is a note for the future rather than a live hazard.
`NamedParameterJdbcTemplate` still joins the ambient transaction through
`DataSourceUtils`, so the service's transaction boundary is unchanged. There is
precedent for dropping to SQL when the entity graph is the wrong tool:
`StoredImageRepository.findForList` already does.

The LIKE pattern is built in one Java helper that doubles `\` and escapes `%`
and `_`, with `ESCAPE '\'` in the SQL. An unescaped `%` in a member's query
turns their search into "match everything" and an unescaped `_` into a silent
single-character wildcard; both are a unit test rather than a comment.

The tag arm of the fallback (`EXISTS (SELECT 1 FROM unnest(tags) t WHERE t LIKE
...)`) is **not** index-assisted - a trigram index cannot span array elements. It
is acceptable because it only runs for single-token queries, over an
already-grant-restricted set, at household scale. If it stops being acceptable
the escape hatch is a `data_entry_tag` child table carrying its own trigram
index *in addition to* the array; the array has to stay regardless, because
adr/0030's generated vector cannot see a child table.

0006's consequence 4 is the member-visible cost: a three-letter query will
sometimes surface a word nobody was thinking of. It will always be below the
exact matches.

## Alternatives considered

**`@Query(nativeQuery = true)` on the JPA repository.** Keeps the SQL next to
the other queries. Rejected because the result is a projection carrying the
collection's name and icon rather than an entity, binding a `text[]` through a
Hibernate native parameter is a fight where `setObject` is one line, and the
explicit casts nullable parameters need read far better in a plain string than
inside an annotation.

**Two statements, one per route.** Simpler to read individually. Rejected on the
only ground that matters: the scoped one would gain a feature the global one
did not, and nothing would fail.

**`pg_trgm` as a co-equal arm, ranked by `similarity()` alongside `ts_rank`.**
A single blended score, and genuinely better at typos. Rejected because a fuzzy
score and a relevance score are not on the same footing, and tuning the blend is
open-ended work whose failure mode is an exact match that is not the first
result - the one outcome a search box cannot afford.

**Trigram over every searchable field, including `detail_text`.** Would make
R55 true of the detail body too. Rejected on index size and on scope: nobody
asked to find a word by its middle inside a recipe's directions, and the
trigram index on a long text column is large and slow to build.

**A separate search service - Elasticsearch, Meilisearch, or similar.** Better
at this than Postgres is, and a third piece of infrastructure to provide,
secure, back up and keep in sync for one household's recipes. Postgres is
already provided in both environments (adr/0003's reasoning about Redis applies
unchanged), and the scale here is thousands of rows.
