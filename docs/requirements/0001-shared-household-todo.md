# 0001 - Shared household todo list

Status: accepted
Date: 2026-11-04

## Problem

A household keeps track of chores and errands informally - verbally, or in one
person's private notes app. Work gets duplicated, forgotten, or silently assumed
to be someone else's. There is no shared place a family can all see and edit.

## Who uses it

Members of a single household. Everyone is a peer: there is no manager role, and
any member may read and edit any list in their household.

## Requirements

### Must have

- **R1 Lists.** A member can create a list and name it. Lists group items by
  whatever the family finds useful (Groceries, Garden, This weekend). Grouping is
  core, not decoration.
- **R2 Items.** A member can add an item to a list, edit its text, and delete it.
- **R3 Completion.** Any member can mark any item complete, and un-complete it.
- **R4 Assignment.** An item may be assigned to one member, or to nobody.
- **R5 Shared visibility.** Every member sees every list in their household,
  including changes other members have just made.
- **R6 Household isolation.** A member can never read or write another
  household's lists or items.
- **R7 Sign-in.** A member signs in before seeing anything.

### Explicitly not required

- Per-list permissions, private lists, or roles beyond "member".
- Comments, attachments, recurring items, reminders, due-date notifications.
- Offline use.

## Behaviour under concurrency

Two members acting on the same item at the same time is normal, not an edge
case. Completing or reassigning the same item concurrently must not silently
discard one member's action; the second writer is told their view was stale
rather than having their change vanish. See
[design/0001-skeleton-architecture.md](../design/0001-skeleton-architecture.md).

## Open questions

- How does a member join a household - invite link, or an existing member adding
  an email? Needed before R6 can be fully specified.
- Should a completed item stay visible in the list, or move out of the way?
