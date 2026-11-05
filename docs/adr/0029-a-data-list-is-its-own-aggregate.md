# 0029 - A data list is its own aggregate, not a kind of todo list

Status: accepted
Date: 2026-11-05

## Context

Requirements 0006 adds **data lists**: reference collections - recipes,
restaurants, quotes, vocabulary - whose entries are never assigned, dated,
completed, scored or generated on a schedule (R50).

Structurally a collection looks like a todo list wearing fewer clothes. It has a
name, a description, an icon, an owner, per-member access grants (R50 reuses R19
verbatim), and children. The obvious move is a discriminator: add
`kind TODO | DATA` to `todo_list`, reuse `list_access`, `list_user_state`, the
icon set and the whole authorization layer, and get collections for the price of
a column.

What makes that tempting is also what makes it dangerous. `todo_list` is read by
nine things that have never heard of a collection, and a collection must not
appear in any of them:

- `TodoListRepository.findVisible` and `findVisibleById` - the list of lists
- `TodoListService.sidebar`, and `ListUserStateRepository`'s pinned and recent
- `DashboardService`, `TodoItemRepository.findScheduledFor` / `findUndatedFor`
- `ListFieldService`, `RecurrenceRuleService`, `StoredImageRepository.findForList`

Every one of those would need a `kind` filter added, and the cost of forgetting
one is not a failure. It is a recipe appearing in tomorrow's dashboard, or the
recurrence job finding a collection. `TodoListService.create` is worse than a
missing filter: it unconditionally writes a `DESCRIPTION` and a `DUE_AT`
`list_field` row for every list it creates (adr/0022), so a collection created
through it would own a due-date field that requirements 0006 says cannot exist.

This is the failure class CLAUDE.md already warns about for `completed_at`
versus `done` (adr/0024): *a new reader written against the wrong thing will
compile, pass, and be wrong*. A discriminator makes that the default for every
future query against `todo_list`.

## Decision

A data list is its own aggregate. New tables `data_list` and `data_list_access`,
a new `data_entry`, and a new `com.hatodo.data` package with its own service,
controller and exception advice. No `kind` column on `todo_list`, and no
collection row reachable from any existing query.

`data_list_access` is a deliberate, verbatim copy of `list_access`'s schema, and
`DataListService` a deliberate copy of `TodoListService`'s two predicates -
`readable` (owner, or a grant row) and `managed` (owner, and still not
`|| isAdmin()`, for adr/0010's reason). The duplication is the point: the two
features answer the same question about different things, and sharing the
implementation is what would couple them.

`ListIcon` **is** shared, because it is a closed set of drawings with no
behaviour, and `com.hatodo.richtext` is shared because the sanitizing rule must
have exactly one implementation (adr/0018).

## Consequences

Every existing read of `todo_list` is correct without being changed, and stays
correct when somebody adds the tenth one. A collection cannot be assigned, dated,
completed or picked up by the recurrence job, because there is no column to put
those in - the exclusions in 0006 are enforced by the schema rather than by nine
filters that have to agree.

The cost is roughly 150 lines of a pattern the repository has already written
and already unit-tested twice, plus a second grant table. `DataListServiceTest`
carries a `createDoesNotWriteAnyListFields` test as the guard against the
discriminator design creeping back in by accident.

Collections get **no pins and no recency**, because `list_user_state.list_id` is
a foreign key to `todo_list` and must not be loosened into a polymorphic
reference - the cascade is the only thing guaranteeing that a pin cannot outlive
its list. 0006's consequence 5 states what that costs: the share picker cannot
preselect a collection. If per-member state on a collection is ever wanted, the
answer is a `data_list_user_state` table mirroring the existing one, not a
widened foreign key.

Two features now mean two places to change when the grant model itself changes -
if grants ever gain a role, both tables learn about it. That is a real cost, and
it is smaller than the cost of every cross-list read having to remember a
discriminator.

## Alternatives considered

**`kind TODO | DATA` on `todo_list`.** Rejected above: nine existing readers
become wrong-by-omission, `create` writes fields a collection cannot have, and
`ux_todo_list_owner_name` would make "Restaurants" the collection and
"Restaurants" the todo list mutually exclusive for one owner - which is not a
constraint anybody asked for.

**Single-table inheritance in JPA over one table.** The same problem with a
Hibernate discriminator instead of a hand-written filter. Hibernate would add
the `kind` predicate to queries issued through the typed repositories, but not
to the native and JPQL queries that join `list_access` directly, and the places
most likely to be forgotten are exactly those.

**A shared `list` supertype table with `todo_list` and `data_list` extending
it.** Honest about the commonality, and it buys one grant table instead of two.
Rejected because every read of a todo list becomes a join, the `@Version`
column has to live on one of the two tables and whichever is chosen is wrong for
some write, and the project has no other joined-inheritance mapping to pattern
it on. The duplication it saves is a table definition; the complexity it adds is
on every query in the oldest part of the product.
