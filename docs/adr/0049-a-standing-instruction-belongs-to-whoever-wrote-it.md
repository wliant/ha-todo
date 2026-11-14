# 0049 - A standing instruction belongs to whoever wrote it

Status: accepted
Date: 2026-11-15

## Context

Writing a recurrence rule has needed ownership of the list since 0005 R41, on
0004 R20's reading that the shape of a list is the owner's business. Everything
else a member with a grant can do - add an item, assign it within the list,
complete or lapse anybody's - is open to every member with access.

Requirements 0016 R141 says that is the wrong line. A field constrains every
item anybody adds; a rule only adds items, and adding items is the one thing a
grant has always meant. So the product allows the larger act and refuses the
smaller one.

Opening it up is one word at the create site. The real decision is what happens
*afterwards*, once a list can hold rules written by four different people, and
where the answer is written down. Three constraints were real:

- The refusals must not become discoverability. A rule on a list the caller
  holds no grant on has to answer 404, as it does today; only a rule they can
  *see* but not write may answer 403. The current code gets this right by
  catching `requireManaged`'s exception and asking `isReadable` to decide
  whether to downgrade it.
- The client has to draw the edit control on exactly the rules the server will
  accept a write for. A control that 403s is a bug report; a missing control on
  a rule the member may edit is worse, because nothing fails anywhere.
- `recurrence_rule.created_by` has existed since `V910` and has never been read
  for anything. It is nullable, `ON DELETE SET NULL`.

## Decision

**Creating a standing instruction needs read access. Editing and deleting one
needs to be its author, or to own the list.**

The two-clause predicate is written **once**, as a method on the entity:

```java
public boolean isWritableBy(UUID userId) {
    return list.isOwnedBy(userId)
            || (createdBy != null && createdBy.getId().equals(userId));
}
```

Two callers ask it: the service's write path, which refuses with a new
`RecurrenceRuleNotWritableException` (403), and `RecurrenceRuleResponse`, which
publishes the answer for this caller as an `editable` flag. The DTO's factory
therefore takes the caller's id - `of(rule, callerId)` - which breaks every
call site at compile time rather than defaulting to something.

The private helper that guards `update` and `delete` is renamed `managed` →
`writable` and restructured: it asks `isReadable` **first** and throws 404, then
asks the predicate and throws 403. The old shape - call the throwing ownership
check, catch, then decide - cannot express the new rule, because ownership is no
longer the question.

An authorless rule (its author's account deleted) is the owner's alone.

## Consequences

- One definition of who may write a rule exists in the backend, and the client
  has none at all - it renders a flag. The alternative asked for a third copy of
  the predicate in TypeScript, assembled from `list.owned` and
  `useCurrentUser()` across two separate requests.
- `isWritableBy` touches `rule.list`, which is `LAZY`. Every current caller has
  the list in the same persistence context - the service loaded it, or the rule
  came from a repository query inside the transaction - so this is one
  association access, not an N+1. A future caller that maps a large batch of
  rules outside a transaction is where that stops being true, and the mapping
  would fail loudly with a `LazyInitializationException` rather than quietly.
- The 403's message changes from "Only the list's owner can change what recurs
  on it", which is now untrue. `ListNotManagedException`'s handler in
  `RecurrenceExceptionHandler` is **replaced** rather than kept beside the new
  one: nothing in the recurrence paths calls `requireManaged` any more, and a
  handler for an exception that can no longer be thrown is a sentence nobody
  can get the product to print.
- A member can now create recurring work for the household. Bounded by the
  list's grants, visible with its author's name on it (R142), and removable by
  the owner (R143).
- A rule survives the revocation of its author's grant, editable by the owner.
  Consistent with what revocation already does - it unassigns, it does not
  delete.
- No migration. The column was already there, which is the only reason the
  author half of this is cheap.

## Alternatives considered

**Any member with a grant edits any rule.** Fully symmetric with items, and the
simplest possible code - `requireReadable` everywhere, no predicate, no
`editable` flag, no per-row gating. Lost because the symmetry is false:
completing an item reports a fact about the world, while editing a standing
instruction changes what the list will do next month on somebody else's behalf.
A household of four does not need the product to arbitrate, but it does need
"who changed the bin day" to have an answer, and an open edit path removes the
author along with the restriction.

**Create only; the owner still edits everything.** Smaller still, and it leaves
a member unable to fix a typo in a rule they wrote ten seconds ago. The failure
mode is "delete it and write it again", which the owner cannot help with either
because they do not know what was meant.

**A `canWrite` flag computed in the service rather than on the entity.** Same
single definition, but the service would have to hand it to the DTO factory
*and* use it for the refusal, which is two parameters threading one boolean
through a mapping layer. On the entity it is a question the aggregate can answer
about itself, which is also where `TodoList.isOwnedBy` already lives.

**A per-list setting for whether members may write rules.** Declined in the
requirement. It is a switch nobody will find, defending a household of four
against a disagreement they would have out loud.
