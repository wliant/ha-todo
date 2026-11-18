# 0052 - A reading is a number in a declared bucket

Status: accepted
Date: 2026-11-19

## Context

0019 R162 and R163 say a reading is one number in one bucket, that several
readings may share a bucket, and that a declared rollup folds them. R166 says a
bucket is the household's. Three decisions fall out of that and all three have
a quiet failure mode.

**Where the bucket boundary is computed.** The tracker declares DAY, WEEK or
MONTH, and the form sends a date. A monthly tracker's form could send the 18th
as easily as the 1st, and a stored `2026-11-18` would sit beside `2026-11-01`
as a *second November* that every fold would treat as a different bucket. The
tracker's period lives in another table, so a `CHECK` cannot see it, and
`AT TIME ZONE` is STABLE rather than IMMUTABLE so Postgres refuses it in a
generation expression - the same wall `occurs_on` hit (adr/0036).

**Whose series a reading is in**, on a feature where one table holds per-member
series and a household one.

**What one reading per bucket would cost.** A unique index on `(tracker,
member, period_start)` is the obvious shape and makes a second number in a
bucket either an error or a replacement. It is also the shape that cannot
express "three shopping trips in November".

## Decision

**`period_start DATE`, canonicalised by the service.** `TrackerPeriod.startOf`
is the one implementation - the day itself for DAY, the Monday for WEEK, the
first for MONTH - applied to whatever the request carried, against
`HouseholdProperties.zone()`. Monday rather than Sunday because
`web/src/dashboard/calendar.ts:startOfWeek` is already Monday, and a week
measured two ways is adr/0036's "two definitions of Thursday" with a new face.

**Nothing in this feature puts an instant on the wire.** `period_start` is a
`DATE` in the column, a `LocalDate` in Java and a `YYYY-MM-DD` string in the
DTO, and the client holds it as that string. The `DueAtField` trap - `new
Date(localString)` silently speaking the *device's* zone (0012 R96) - cannot
occur, because there is no local-datetime input anywhere in the feature.

**`member_id` nullable, and NULL *is* "the household's".** On a `PER_MEMBER`
tracker every reading carries the member whose series it is in; on a `SHARED`
tracker every reading carries NULL. There is deliberately **no companion
boolean**, for the reason `password_hash IS NULL` has none (adr/0005): a flag
that can disagree with the data eventually does. It also makes the fold one
grouping with no branch - a shared tracker simply yields one series.

Authorship is a separate column, `recorded_by`. On a shared tracker it is the
only record of who typed a number, and it is what R172 keeps when a member is
deleted while `member_id` cascades.

**No unique index on `(tracker_id, member_id, period_start)`.** Several
readings per bucket is the product (R163), and the declared rollup - SUM,
AVERAGE or LATEST - is what resolves them. The same rollup folds *across*
buckets when a range is drawn coarsely, so there is one rule with one
implementation and two uses.

The fold happens **in Java**, over a window capped at 2000 readings. LATEST in
SQL wants `DISTINCT ON` and a native query, and `date_trunc` would be a second
implementation of `TrackerPeriod.startOf` living in the database - adr/0044's
reasoning, where a household's data is not big enough to justify a second
implementation of a predicate.

## Consequences

Two invariants are **service-enforced rather than constrained**, which is the
adr/0022 precedent - "every list declares a due date" lives in
`ListFieldService` because SQL cannot say it without a trigger:

1. `member_id IS NULL` if and only if the tracker is `SHARED`.
2. `period_start` is always the canonical start of its bucket.

Both fail **silently** if broken. A stray `member_id` on a shared tracker is
invisible to every read; an uncanonical `period_start` is a second November
that no error reports. So both are pinned by **integration** tests that write
through the API against a real Postgres - the `setweight` lesson (adr/0033):
probe the database rather than trusting the description.

The absent unique index is a product rule written as an absence, which is the
easiest kind to "fix". The migration says so in a comment and so does the
design document: adding that index later is a product change, not a tidy-up.

The 2000-reading cap is a safety bound, not pagination. Hitting it is the
threshold for moving the fold into SQL, and until then it is five years of
daily readings for four members.

## Alternatives considered

- **One reading per bucket, replaced on re-record.** Simpler, enforceable by
  two partial unique indexes, and wrong for the grocery case: the member would
  have to add up their own receipts, which is the arithmetic the feature exists
  to do.
- **`member_id NOT NULL`, with a sentinel "household" user.** Rejected - a row
  in `app_user` that is not a person is a trap for every join that counts
  members.
- **A `shared BOOLEAN` beside `member_id`.** Two columns that can disagree.
- **`period_start` as a generated column.** Refused by Postgres (`AT TIME ZONE`
  is STABLE), and it could not read the tracker's period anyway.
- **Folding in SQL with `date_trunc` and `DISTINCT ON`.** Faster at a size this
  product does not reach, and a second place where a week's start is decided.
