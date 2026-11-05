# 0033 - Tags enter the search document stemmed and positioned

Status: accepted
Date: 2026-11-05
Supersedes: [0030](0030-an-entrys-searchable-document.md)

## Context

[0030](0030-an-entrys-searchable-document.md) decided how an entry's
`search_vector` is built, and got one of the four pieces wrong. The error was
found by probing a real Postgres 17 before writing the migration, which is the
only reason it was found at all: the expression it specified is valid SQL,
creates without complaint, and indexes without complaint. It simply does not do
what the ADR said.

0030 put tags into the document as `setweight(array_to_tsvector(tags), 'B')`,
reasoning that `array_to_tsvector` is the one immutable way to get an array into
a generated `tsvector` and that not stemming tags keeps them precise. Both
halves of that are wrong in a way that matters.

**`setweight` cannot weight a position-less lexeme.** `array_to_tsvector`
produces lexemes with no positions - `'italian' 'quick'`, not `'italian':1
'quick':2` - and a weight in Postgres is a property of a *position*. `setweight`
on such a vector is accepted and does nothing. Measured:

```
tag hit (via array_to_tsvector, "weight B")   rank 0.057308756
name hit (weight A)                           rank 0.378081260
detail hit (weight D)                         rank 0.057308756
```

A tag hit ranked **identically to a detail-body hit** - the bottom band - while
0030 claimed it ranked second only to the name. The ranking the design promised
was not happening, and nothing failed to say so.

**An unstemmed tag is close to unreachable by full text.** The query side is
always stemmed, so a stored lexeme that is not its own English stem can never be
matched by a member typing it. For the tag `desserts`:

```
query 'dessert'    matches tag 'desserts'?   f
query 'desserts'   matches tag 'desserts'?   f      <-- the member's own word
query 'dessert:*'  matches tag 'desserts'?   t      (by accident of prefix)
```

The member types the exact tag they created and does not find it. 0030 described
this as an accepted cost - "a tag is an exact word" - but what it accepted was a
different, smaller thing: that `dessert` and `desserts` remain two distinct
tags in the suggestion list. That part is about tag *identity* and is still
true. Being unable to find either one is requirement 0006 R53 not working, and
it only looked fine in the ADR because the worked example, `italian`, happens to
be its own stem.

## Decision

Tags enter the document the way the other three fields do: as **stemmed,
positioned text**, from a column the application maintains.

```sql
ALTER TABLE data_entry ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')),        'A')
     || setweight(to_tsvector('english', coalesce(tags_text, '')),   'B')
     || setweight(to_tsvector('english', coalesce(description, '')), 'C')
     || setweight(to_tsvector('english', coalesce(detail_text, '')), 'D')
    ) STORED;
```

`data_entry.tags_text` is the normalised tags joined by a space, written by
`DataEntry.setTags` - the same setter that writes `tags`, and the only writer of
either. This is 0030's `detail_text` discipline applied a second time, for the
same reason: the application computes the searchable form, the database
generates the vector from it, and no caller can write one without the other.

`array_to_tsvector` is no longer used. `tags text[]` stays exactly as it was,
because it is what R56's filter chips match with `@>` - **exactly**, with no
stemming - and what the tag-suggestion endpoint unnests. Exact filtering and
stemmed searching are two different jobs and now have two different mechanisms
instead of one doing both badly.

Everything else 0030 decided stands and is restated here so this ADR can be read
alone: the vector is `STORED` and generated, not trigger-maintained; the
generation expression must be `IMMUTABLE`, so `to_tsvector` is always called
with an explicit `'english'` config; `detail_text` is application-maintained
because the only immutable HTML-stripping expression available is a regexp and
`regexp_replace(detail_html, '<[^>]*>', '', 'g')` turns
`<li>milk</li><li>sugar</li>` into the single word `milksugar`; `search_vector`
is not mapped in JPA at all; and element rules on tags are enforced in
`TagNormaliser` because SQL cannot state them without a trigger.

## Consequences

Weight B is now real, and measurably so - for the query `dessert`, an entry
tagged `desserts` ranks `0.233` against `0.057` for an entry merely mentioning
desserts in its detail. The A-B-C-D ordering the design promised is the ordering
that happens.

A member who types a tag finds it, whether or not the tag is its own stem, and
whether they type the singular or the plural. A search for `dessert` also finds
entries tagged `desserts`, which is the behaviour a member expects from a search
box and the opposite of what 0030 would have shipped.

Requirements 0006's consequence 3 needs reading more narrowly than it was
written. "A tag is an exact word" is true of **filtering and identity**:
`desserts` and `dessert` are two separate tags, both appear in suggestions, and
a chip filters on exactly one of them. It is **not** true of search, where both
tags are reachable from either word. That is a better product than the one the
consequence described, and the suggestions in R52 remain what keeps a collection
from accumulating both spellings.

The cost is a third application-maintained column, and a third opportunity for
Java to write one column and not another. The mitigation is the one that already
exists: `setTags` writes both, there is no setter for `tags_text`, and a unit
test asserts there is no way to write one alone.

One general lesson worth keeping, since it is the second time this feature has
turned on it: a generated column that is *accepted* by Postgres is not evidence
that it computes what was intended. `setweight` over a position-less vector is
silently inert, exactly as a regexp-stripped `detail_html` would have silently
merged words. Both were caught by querying a real database before writing a
migration, and neither would have been caught by a test written against the
schema the ADR described.

## Alternatives considered

**Keep `array_to_tsvector` and additionally stem the tags into the vector.** Both
forms present: exact lexemes plus stemmed ones. Rejected as redundant - the
stemmed form matches everything the exact form does and more - and because the
exact arm still cannot carry a weight, so it would contribute hits ranked at the
bottom band that the stemmed arm already contributes near the top. Two entries
for one tag in one vector, for no gain.

**Give the tags their own `tsvector` column and `@@` it separately.** Would let
tags rank independently of the main document. Rejected because ranking then has
to be combined in the query by hand, which is adr/0031's rejected
"co-equal arm" problem in a new place, and because one document with four
weights is exactly what `ts_rank` and `setweight` exist for.

**Accept 0030's behaviour and lower the documented expectation.** Write the
requirement down as "tags are matched exactly, type them as stored". Rejected
because it is not achievable even on its own terms: the query side stems
unconditionally, so "type them as stored" fails for `desserts` no matter what
the member does. There is no wording that makes the shipped behaviour correct.

**`to_tsvector('english', tags::text)` instead of a maintained column.** Avoids
the extra column. Rejected: the array-to-text cast goes through `array_out`,
which is `STABLE` for the same reason `array_to_string` is, so the generation
expression is refused - the identical failure 0030 already documented, in a new
disguise. It also indexes the braces and quotes of the array literal.
