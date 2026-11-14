# 0016 - Anyone with access writes standing instructions

Design for [requirements/0016](../requirements/0016-anyone-with-access-writes-standing-instructions.md)
(R141-R147). A small feature with one sharp edge: the authorization rule it
replaces is a single word in one place today, and the rule that replaces it is
two clauses that have to be asked in two places - the write path and the DTO -
without becoming two different rules.

Decisions this implements rather than argues:
[adr/0049](../adr/0049-a-standing-instruction-belongs-to-whoever-wrote-it.md).

## Scope

No migration. `recurrence_rule.created_by` has existed since
`V910__recurrence.sql` - it was recorded from the start and never read for
anything but the record. That is the whole reason this is cheap.

Four steps:

1. **The predicate** - one method on `RecurrenceRule`, so the write check and
   the DTO's flag cannot drift.
2. **The service** - `create` drops to `requireReadable`; the private
   `managed(ruleId)` becomes `writable(ruleId)` and keeps its 404-before-403
   shape.
3. **The DTO and the client** - `createdBy` and `editable` on the response, the
   author on each row, and the controls gated per row rather than per list.
4. **Docs and tests** - this, the requirement, the ADR, `CLAUDE.md`, and the
   three tests that currently assert the old rule by name.

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| The author, recorded | `recurrence_rule.created_by`, `RecurrenceRule.getCreatedBy()` | R142 and R143 need no schema change at all |
| Read access, as a service call | `TodoListService.requireReadable` / `isReadable` | R141 is a one-word change at the create site |
| Ownership, as a question of the list | `TodoList.isOwnedBy(userId)` | The owner half of R143 is already a method on the aggregate the rule holds |
| The 404-before-403 shape | the private `managed(UUID)` in `RecurrenceRuleService` | Keeps R143's refusals from making an invisible list discoverable |
| A member DTO | `ItemMemberResponse` | R142's author renders with the avatar the rest of the app uses |
| The caller, without threading it | `CurrentUserProvider` (`adr/0016`) | The service already holds the only thing the predicate needs |
| Per-row controls on a list | `ItemsSection`, `useCurrentUser` | The pattern for "this row, not that one" exists |

## Step 1 - the predicate

One method, on the entity:

```java
/**
 * Whoever wrote it, or whoever owns the list it is on (R143).
 */
public boolean isWritableBy(UUID userId) {
    return list.isOwnedBy(userId)
            || (createdBy != null && createdBy.getId().equals(userId));
}
```

It lives on `RecurrenceRule` rather than in the service because **two callers
need the same answer and must not each spell it out**: the write path, which
refuses, and `RecurrenceRuleResponse`, which tells the client whether to draw
the control. Two copies of a two-clause predicate is how a member ends up with
an edit button that 403s, or - worse, because nothing fails - no button on a
rule they are perfectly entitled to edit.

`createdBy` is nullable in the schema, so the null branch is real rather than
defensive: `ON DELETE SET NULL` is what happens to the author of a rule when
the account is deleted. An authorless rule is the owner's alone, which is the
right answer.

## Step 2 - the service

`create` changes one call:

```java
TodoList list = lists.requireReadable(listId);
```

Nothing else in `create` moves. The rule is still constructed with `me` as its
author, which is now load-bearing rather than decorative.

The private `managed(UUID ruleId)` becomes `writable(UUID ruleId)`, and keeps
the structure it has for the reason it has it - a rule on a list the caller
cannot see must be a 404, not a 403:

```java
private RecurrenceRule writable(UUID ruleId) {
    RecurrenceRule rule = rules.findById(ruleId)
            .orElseThrow(() -> new RecurrenceRuleNotFoundException(ruleId));
    // As invisible as the list it is on: an ungranted list must not be
    // discoverable through the rules hanging off it.
    if (!lists.isReadable(rule.getListId())) {
        throw new RecurrenceRuleNotFoundException(ruleId);
    }
    if (!rule.isWritableBy(currentUser.requireUserId())) {
        throw new RecurrenceRuleNotWritableException(ruleId);
    }
    return rule;
}
```

Note what this *stops* doing: the old version called `requireManaged` and then
caught the exception to decide whether to downgrade it to a 404, because
`requireManaged` conflated "cannot see" with "does not own". Asking
`isReadable` first is both clearer and the only way to express the new rule,
since ownership is no longer the question being asked. `isReadable` exists
precisely for this - catching the throwing variant leaves the transaction
marked rollback-only, which is why it was written.

`RecurrenceRuleNotWritableException` is new and maps to 403 in
`RecurrenceExceptionHandler`, beside the 404 and 409 already there. Reusing
`TodoListService`'s ownership refusal would have said "you do not own this
list", which is now the wrong reason.

`update` and `delete` call `writable` and are otherwise untouched; the version
check still runs after it, so a stale editor who is also unauthorised is told
about the authorisation first. `list` and the run endpoint do not move at all
(R146).

## Step 3 - the DTO and the client

`RecurrenceRuleResponse` gains two components:

- `createdBy` - an `ItemMemberResponse`, null for an authorless rule (R142).
- `editable` - whether *this caller* may write it (R144).

`of(RecurrenceRule)` becomes `of(RecurrenceRule rule, UUID callerId)`, which
breaks all three call sites at compile time; all three are in the service,
which holds `currentUser`. The flag is computed by the predicate from step 1,
so there is exactly one definition of who may write a rule in the backend.

An alternative was letting the client work it out from `list.owned` and a
comparison against `useCurrentUser()`. Rejected: it is a third copy of the
predicate, in another language, derived from two separate requests.

On the client, `RecurrenceRule` gains `createdBy: ItemMember | null` and
`editable: boolean`, and `RecurrenceSection`:

- draws "Add a recurring item" unconditionally - the `list.owned` gate goes.
- draws the edit control when `rule.editable`, not when `list.owned`.
- shows the author as a chip beside the frequency, reading `by <name>`, omitted
  when the author is the signed-in member. A list where somebody sees "by Ana"
  on four of their own rules has learnt nothing; the name is there to answer
  "who, if not me".
- loses the two-branch empty state. "Nothing recurs on this list yet. Its owner
  sets these up." was true and is now false, and the remaining branch is the
  same sentence for everybody.

The dialog's Delete button needs no new gate: it is only reachable from the
edit control, which R144 already hides.

## Testing

Backend, in `RecurrenceApiIntegrationTest`, because this is an authorization
rule and the only honest test of one is over HTTP with real sessions:

- `onlyTheOwnerWritesAListsStandingInstructions` is **replaced**, not amended -
  it asserts the rule being removed, by name, with R20 in a comment. In its
  place: a member with a grant adds a rule and gets 201.
- A member edits and deletes **their own** rule on a list they do not own.
- A member gets **403** editing and deleting the *owner's* rule.
- The **owner** edits and deletes a **member's** rule - the other half of R143,
  and the half that is easy to leave out.
- A stranger still gets **404**, not 403, editing a rule by id on a list they
  cannot see. This is the test that protects the ordering in `writable`.
- `editable` is `true` and `false` in the right places on the listing, since
  the client's controls depend on it.

Frontend, in `RecurrenceSection.test.tsx`: the existing "lets a member who does
not own the list look and run, but not write" is replaced by one that asserts
the Add button *is* there on an unowned list and that the edit control follows
`editable` per row, plus one for the author chip appearing for somebody else's
rule and not for the caller's own.

E2E, in `e2e/tests/test_recurring.py`:
`test_only_the_owner_writes_the_standing_instructions` becomes
`test_a_member_writes_their_own_standing_instruction_and_not_the_owners` - two
browser contexts, which is the suite's reason to exist: Ben adds a rule on
Ana's list, sees it with no author chip, can edit it, and has no edit control on
Ana's.

## What this deliberately does not do

- **No migration, no new column.** `created_by` was there.
- **No change to the list's configuration endpoints.** R147.
- **No notification.** R142's author chip is the whole of "who did this".
- **No reordering, no transfer of authorship.** Both out of scope in the
  requirement.
- **No change to the run endpoint or the scheduled job.** The job has no
  caller (`adr/0025`) and asks nobody's permission; nothing in this touches it.
