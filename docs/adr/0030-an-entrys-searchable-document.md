# 0030 - An entry's searchable document is generated from text the app maintains

Status: accepted
Date: 2026-11-05

## Context

0006 R53 says search reads the whole entry: its name, its description, the
**text of its detail**, and its tags. The detail is sanitized HTML with bullets,
numbered lists and embedded pictures (R51), and the tags are a free-form set
(R52). Postgres full text search needs all of that as one `tsvector`, indexed,
and kept current through every edit.

Three things constrain how that vector can be built.

**A generated column's expression must be `IMMUTABLE`.** That rules out most of
the obvious spellings. `to_tsvector(text)` - the one-argument form - reads
`default_text_search_config` and is only `STABLE`, so Postgres refuses it;
`to_tsvector('english', text)` is immutable and is accepted. Equally,
`array_to_string(tags, ' ')` is `STABLE`, because an element's output function
may depend on session settings, so the natural way to fold tags into stemmed
text fails at `ALTER TABLE` rather than at query time.

**Stripping HTML with an immutable expression is possible and wrong.** The only
tool available inside a generation expression is a regexp, and
`regexp_replace(detail_html, '<[^>]*>', '', 'g')` turns
`<li>milk</li><li>sugar</li>` into the single word `milksugar` - which is then
one lexeme matching nothing a member will ever type. It also leaves `&eacute;`
indexed as the lexemes `eacute` and whatever surrounds it. Feeding the raw HTML
straight to `to_tsvector` is less bad than it sounds, because the default text
search parser does recognise and discard tags, but it shares the entity problem
and leaves no clean text behind for a future snippet. Turning tags into spaces
and decoding entities is a small parser, not an expression.

**A generated column cannot see another table.** Tags in a `data_entry_tag`
child table are tags that cannot be part of the generated vector - which would
mean either a trigger maintaining the vector, or tags searched by a separate
predicate that ranks separately from everything else.

## Decision

`data_entry.search_vector` is a `STORED` generated `tsvector`, weighted by where
each piece of text came from:

```sql
setweight(to_tsvector('english', coalesce(name, '')),        'A')
|| setweight(array_to_tsvector(tags),                        'B')
|| setweight(to_tsvector('english', coalesce(description, '')), 'C')
|| setweight(to_tsvector('english', coalesce(detail_text, '')), 'D')
```

indexed with GIN. It reads from two columns the **application** maintains:

- **`detail_text`** - the detail's HTML with the markup taken out, element
  boundaries turned into spaces and entities decoded, by
  `com.hatodo.richtext.HtmlText.toPlainText`. It is an ordinary writable column,
  not a generated one.
- **`tags text[]`** - an array, not a child table, lowercased, trimmed,
  de-duplicated and sorted by `TagNormaliser` before it is stored, and entered
  into the vector by `array_to_tsvector`, which does not stem and does not case
  fold.

`DataEntry` exposes **one** setter for the detail, `setDetail(html, text)`, and
no `setDetailHtml` and no `setDetailText`. Writing one without the other is not
something a caller can express.

## Consequences

The vector is maintained by the database, so no edit path can forget to update
it and no trigger has to be read to understand what is indexed. The weights mean
a hit on an entry's name outranks a hit buried in its detail, which is the
difference between a result list and a pile.

`detail_text` is a second representation of the same prose, and 0006's
consequence 2 accepts that. The single `setDetail` setter is what makes
disagreement unrepresentable rather than merely unlikely: there is no code path,
present or future, that can write one column alone.

Note the asymmetry with `TodoItem.done` (adr/0024), which is *also* a generated
column but is mapped `insertable = false, updatable = false` because the
database computes it from columns Java writes. Here the database computes
`search_vector` the same way, but `detail_text` is an ordinary column Java owns.
`search_vector` itself is **not mapped in JPA at all** - Hibernate has no
`tsvector` type, `ddl-auto: validate` ignores a column nobody claims, and the
only thing that reads it is the native search query (adr/0031).

Because `array_to_tsvector` does not stem, a tag is matched exactly: the tag
`desserts` is the lexeme `desserts` and a search for `dessert` does not reach it
through the vector. 0006's consequence 3 accepts this deliberately - a tag whose
meaning the product reinterprets is a tag a member cannot rely on - and R52's
suggestions plus the substring arm of adr/0031 are how a member lands on the one
their household already uses. Do not "fix" it by folding tags into the
English-stemmed text: that reintroduces the stable-function problem and makes
tag precision worse.

SQL cannot state the element rules - "every tag is lowercase and non-blank" has
no `CHECK` form without a trigger - so `TagNormaliser` enforces them, for the
same reason adr/0022 enforces the always-declared due date in
`ListFieldService.normalise`. What SQL *can* state is stated: no NULL elements
(`array_to_tsvector` rejects one outright, which would be a 500 on insert rather
than a 400) and at most twenty tags.

One thing to verify before writing the migration rather than after:
`array_to_tsvector` must actually be immutable -
`SELECT provolatile FROM pg_proc WHERE proname = 'array_to_tsvector'` must
answer `i`. If a Postgres version ever says otherwise, the fallback is to drop
tags out of the generated vector and match them with a separate `tags &&` or
`tags @>` predicate in the search statement. The rest of this decision survives
that unchanged; only the migration and the ranking of tag hits move.

## Alternatives considered

**A trigger maintaining the vector.** Works, allows tags in a child table, and
is how this was done before generated columns existed. Rejected because the
schema stops explaining itself: the column's content lives in a function body
somebody has to go and read, and the project has no triggers at all today.

**`tsvector` maintained by the application.** Every write computes and stores
the vector. Puts the text-search configuration in Java, where it can disagree
with whatever the query uses, and makes a backfill of changed weights an
application migration rather than a `DROP`/`ADD COLUMN`.

**Indexing the raw `detail_html` directly.** One fewer column, and the text
search parser does discard markup. Rejected on the entity problem - `&amp;` and
`&eacute;` become lexemes - and because there would then be no plain text to
build a result snippet from if the open question in 0006 about snippets is ever
answered yes.

**Tags as a `data_entry_tag` child table.** The normal relational answer, and it
would permit a trigram index across individual tags. Rejected because a
generated column cannot see it, so the vector would need a trigger; and because
a collection keyed `(entry_id, tag)` reintroduces the composite-identity trap
that `item_cost_value` already demonstrates - clearing the collection and adding
a freshly built row for a tag the entry already has is two objects for one
identifier, which Hibernate refuses with `NonUniqueObjectException`. The array
has no identity to collide.
