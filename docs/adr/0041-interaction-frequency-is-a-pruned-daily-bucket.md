# 0041 - Interaction frequency is a pruned daily bucket

Status: accepted
Date: 2026-11-08

## Context

0012 R100 orders the sidebar by how often a member uses each list **over the
last month**, and R98 requires every visible list to be orderable - including
ones with no history at all.

What exists is `list_user_state.last_interacted_at`: one timestamp per (list,
member), rewritten on each visit outside a fifteen-minute window. It can answer
"most recently opened" and nothing else. A rolling window cannot be derived from
a single timestamp, and a plain `interaction_count` column cannot either - a
counter that only goes up makes a habit abandoned in March outrank one formed
last week, forever, which is the failure R100 names.

Collections have no per-member state at all. `adr/0029` made a data list its own
aggregate and `list_user_state.list_id` is a foreign key to `todo_list`, which
must not be loosened.

Anything that keeps per-visit rows grows without bound, and this product
deliberately has almost no background work: `adr/0025` puts exactly one
`@Scheduled` job in the system and rests it on there being exactly one backend
instance. "Add a nightly pruning job" is not a cheap sentence here.

## Decision

One row per (list, member, **household day**), carrying a hit count and the
instant of the last hit in that day:

```
list_interaction_day      (list_id,      user_id, on_day, hits, last_at)
data_list_interaction_day (data_list_id, user_id, on_day, hits, last_at)
```

Frequency is `SUM(hits) WHERE on_day >= today - 30`, in the household's zone.

Three things fall out of the shape, and all three are the reason for it:

1. **The fifteen-minute dedup window needs no other table.** The upsert's
   `ON CONFLICT ... DO UPDATE ... WHERE last_at < now() - interval` reads the row
   it is about to write. The hit counter is therefore independent of
   `list_user_state` entirely, and collections get frequency without getting a
   state table.
2. **It prunes itself.** Each increment also deletes that member's buckets older
   than the window. The rows read are the rows kept; nothing accumulates and no
   job exists to forget to run.
3. **It is bounded by construction.** At most one row per list per member per
   day, so a household of four with ten lists each is a few hundred rows at the
   high-water mark.

`on_day` is a `DATE` in `app.timezone`, computed by the application, for the
reason `occurs_on` is: `AT TIME ZONE` is STABLE rather than IMMUTABLE and
Postgres refuses it in a generation expression (`adr/0036`).

Two tables rather than one with a nullable pair of owner foreign keys, because
`adr/0029`'s separation is the whole point and the duplication is a DDL block,
not logic.

## Consequences

- A list's order in the sidebar now has a decaying memory. A member who stops
  opening a list sees it slide out over a month, which is what R100 asked for
  and is also a thing nobody can see happening - so the window length lives in
  one named constant, not scattered across two queries.
- **The 15-minute window is now enforced by two statements**, this table's
  upsert and `list_user_state`'s, sharing one constant
  (`InteractionWindow.DEDUP_MINUTES`). Sharing the number is fine; sharing a row
  is not. The two can legitimately disagree on whether a visit counts: at 00:02
  `list_user_state` was written an hour ago so the recency write no-ops, while
  this one opens a new day's bucket - which is *correct*, because a visit after
  midnight is a new day's visit. A future change that "unifies" them by making
  one statement read the other's row reintroduces an ordering dependency between
  two writes in one method, and the second one silently stops counting. Keep the
  statements independent.
- Ordering is `pinned`, then window hits, then `last_interacted_at`, then name.
  A brand-new list has zero hits and no recency, so it sorts by name among the
  other zeroes - which satisfies R98 (it is *there*) without pretending it is
  used.
- Lists tie-break on `list_user_state.last_interacted_at` and collections on
  `MAX(last_at)` from their bucket table. Deliberately asymmetric: lists have
  real recency data predating this change and collections have none, so each
  uses the best it has. Both are tie-breaks and neither is load-bearing.
- The prune is scoped to `user_id`, not to `(user_id, list_id)`. Scoping it to
  the list would leave stale buckets for every list the member *stopped*
  opening, which is exactly the set that never gets written again.
- Deleting a list or a member cascades the buckets away; nothing reads them
  afterwards.
- The window read is a `SUM` over up to thirty rows per list on a join the
  sidebar already does. At household scale this is noise. It would not be at
  thousands of lists per member, which this product does not have.

## Alternatives considered

- **`interaction_count` on `list_user_state`.** One integer, no new table, and
  wrong in the way R100 names: monotonic, so it ranks lifetime use and never
  forgets. Rejected on the requirement, not on cost.
- **One row per visit, pruned by a nightly job.** Exact, and it buys precision
  nobody will ever look at, in exchange for unbounded growth between runs and a
  second `@Scheduled` job - which `adr/0025` says costs more than it looks,
  because the single-instance assumption is load-bearing.
- **An exponentially decayed score in one float column**, updated on each
  touch. Constant space and genuinely elegant. Rejected because it is
  uninspectable: there is no query that answers "why is this list third", the
  decay constant is unexplainable to anybody, and a bug in it is invisible.
  Thirty daily buckets can be read with `SELECT *`.
- **Recency only, reusing what exists.** Free, and it is the behaviour 0012
  exists to replace.
