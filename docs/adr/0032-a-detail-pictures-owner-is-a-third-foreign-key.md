# 0032 - A detail picture's owner is a third foreign key

Status: accepted
Date: 2026-11-05

## Context

0006 R61 says a picture embedded in an entry's detail is readable by exactly the
people the entry's collection is readable by. That is adr/0019's rule - a
`DETAIL` picture inherits the access of the thing that refers to it - applied to
a third kind of owner.

`stored_image` currently carries two nullable owner columns. `item_id` arrived in
V907 and `rule_id` in V911, under
`CHECK (item_id IS NULL OR rule_id IS NULL)`, both with
`ON DELETE CASCADE`. Null in both means unclaimed: uploaded while something was
being written, readable only by whoever uploaded it, and claimable only by that
member and only while it is still unclaimed.

A third owner is the point at which "one nullable foreign key per owner" either
becomes the pattern or gets replaced by something that generalises. It is worth
deciding on purpose rather than by adding another column and moving on.

There is also a wiring constraint that is easy to discover too late.
`ImageController` injects `ImageAccess` **by type**, and `ItemImageAccess` is its
only implementation; a second `@Component implements ImageAccess` is a
`NoUniqueBeanDefinitionException` at startup, not a compile error.

## Decision

A third nullable foreign key, `stored_image.entry_id`, referencing `data_entry`
with `ON DELETE CASCADE` and its own partial index.

The exclusivity check is rewritten to a form that does not need rewriting again:

```sql
CHECK (num_nonnulls(item_id, rule_id, entry_id) <= 1)
```

`ImagePurpose` gains **no** new value. The enum answers one question - readable
by anybody, or readable by whoever the owner's list is readable by - and an
entry's picture is the second answer, identical to an item's. A `DATA_DETAIL`
value would duplicate `DETAIL`'s semantics, break the partial indexes defined in
terms of `purpose = 'DETAIL'`, and require a change to `ImageUploadController`,
which needs none.

For the bean-ambiguity constraint, a **second single-method interface** in the
`images` package - `DataEntryImageAccess` - rather than a method added to
`ImageAccess`, whose implementation lives in `com.hatodo.item` and must not
learn about collections. `ImageController.mayRead` becomes a decision on which
foreign key is set: an `AVATAR` is public; an `item_id` asks the item package; an
`entry_id` asks the data package; anything else answers no.

**The threshold for revisiting is a fourth owner.** At four, this is replaced by
an `image_owner` join table or an equivalent that carries the cascade properly.
Stating the number here means the next person inherits a decision rather than a
judgement call.

## Consequences

The cascade keeps doing the job it has been doing: no `stored_image` row can
outlive its owner, guaranteed by the database rather than by every delete path
remembering. Deleting an entry, or a whole collection, takes its picture rows
with it, and the bytes are forgotten by the same `forgetPicturesOf` shape the
item feature already uses.

The exclusivity check is now a count, so adding an owner is one argument rather
than a combinatorial rewrite. The "n foreign keys" shape still grows linearly,
and three is where this stops being free; the stated threshold is what stops it
growing quietly.

One pre-existing bug is fixed in the same migration, because this is the change
that makes it matter. `ix_stored_image_unclaimed` was written in V907 as
`WHERE item_id IS NULL AND purpose = 'DETAIL'` and was never updated when
`rule_id` arrived in V911 - so it already reports every rule-owned picture as an
unclaimed orphan. Nothing sweeps orphans today (adr/0013 tolerates them), so the
index is currently only misleading rather than destructive; a sweeper written
against it would delete the pictures behind every recurrence rule. It is
recreated covering all three owner columns.

Rule-owned pictures answer "not readable" through `ImageController` today, and
keep doing so. That is pre-existing behaviour and not something this change
should quietly alter.

`DataEntryImageAccess` must use `DataListService.isReadable`, not
`requireReadable`. Catching the exception the throwing form raises leaves the
surrounding transaction marked rollback-only and the commit then fails with a
500 where a 404 was the entire point - the same reason `ItemImageAccess` is
written the way it is.

Claiming follows the item rule exactly: only pictures this member uploaded, only
ones with no owner at all, and only by an entry on a collection they can see. An
image id alone must never drag somebody else's picture onto a collection.

## Alternatives considered

**A polymorphic `(owner_type, owner_id)` pair.** The version that looks cleaner
on a whiteboard and is worse in every concrete respect here. It gives up
`ON DELETE CASCADE`, which is currently the only thing guaranteeing an image row
cannot outlive its owner - replacing it with application sweeping means six
delete paths have to remember, and the one that forgets leaves a row claiming an
owner that no longer exists. "Unclaimed" today means "readable only by the
uploader"; a dangling claim would mean "readable by whoever happens to own that
UUID in another table now", which is an access check that cannot be evaluated.
It also gives up referential integrity, making the partial indexes and
`findForList` correct-by-convention. The index-count argument that usually
favours it does not apply: the existing owner columns are covered by *partial*
indexes, so three nullable columns cost three small indexes, not three full
ones.

**An `image_owner` join table now.** Generalises properly and keeps a cascade in
both directions. Rejected as premature at three owners: it turns every read of a
picture's owner into a join, and the schema already made the one-column choice
once in V911. It is the stated answer at four.

**A new `ImagePurpose` value per owner kind.** Rejected above - the enum would
stop answering the question it exists to answer, and the partial indexes keyed on
`purpose = 'DETAIL'` would silently stop covering half their rows.

**Keeping pictures out of entry details entirely.** Would make R61 unnecessary.
Rejected because a photographed label, a dish, or a page of a book is most of why
a collection is worth keeping, and R51 reuses the item's editor precisely so that
capability comes along.
