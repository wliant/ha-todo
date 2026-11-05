# 0011 - Item fields are a typed per-list declaration

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R21, R22

## Context

R21 lets each list declare the shape of its items, because a shopping list and a
chore roster are not the same shape. Description is always present and always
required; detail, assignee, due date and cost are optional, and each enabled field
can be marked required. R22 adds that a list may declare **several** named cost
fields - a chore can cost both time and money.

So there is a schema per list. The question is where it lives and how its rules are
held. Three facts constrain the answer. The set of field *kinds* is closed and
short, and only this codebase can add to it, so this is not user-defined EAV. Field
order is user-visible, because it is the order of inputs in the item form. And the
cost kind carries a sub-configuration - mode, unit, points rate, a set of choices -
that no other kind has.

## Decision

**One `list_field` row per declared field**, with a closed `kind`
(`DESCRIPTION`, `DETAIL`, `ASSIGNEE`, `DUE_AT`, `COST`), a `required` flag and a
`position`. "Enabled" is the existence of the row; there is no `enabled` column
that could disagree with it, for the same reason `password_hash` has no companion
boolean (adr/0005).

The cost sub-configuration lives on the cost field's own row - `cost_mode`
(`FIXED` | `NUMERIC`), `cost_unit_label`, `points_per_unit` - and a `FIXED` field's
selectable choices are rows in `list_field_choice`, each a label with a point
value.

**The kinds split into singletons and cost fields**, enforced by two partial unique
indexes rather than by application code:

```sql
CREATE UNIQUE INDEX ux_list_field_singleton ON list_field (list_id, kind)
    WHERE kind <> 'COST';
CREATE UNIQUE INDEX ux_list_field_cost_label ON list_field (list_id, lower(label))
    WHERE kind = 'COST';
```

A list therefore has at most one description, detail, assignee and due-date field,
and any number of cost fields distinguished by name. A cost field is named and the
singletons are not, which is itself a check: `(kind = 'COST') = (label IS NOT NULL)`.

Every rule that can be a database constraint is one: description exists and is
required (`ux_list_field_singleton` plus `kind <> 'DESCRIPTION' OR required`), cost
configuration appears only on a cost field, a numeric cost names its unit, a fixed
cost has neither unit nor rate, points are non-negative. **One rule is held by the
service** - a `FIXED` cost field has at least one choice - because "at least one
child row" needs a trigger to express in SQL, and a trigger is a worse home for a
rule the API already rejects with a readable message.

**The configuration is replaced by merging on id, never by delete-and-recreate.**
`PUT /api/lists/{id}/fields` sends existing field and choice ids back; the service
inserts only entries without one, deletes only those the client omitted, and
refuses to delete a field or choice that items already reference. Item cost values
are keyed by `list_field_id`, so recreating rows would orphan every item's costs -
this is a data-loss bug, not a tidiness concern, and `ON DELETE RESTRICT` on the
choice reference backs the service up.

**Item values are typed columns, not EAV.** The singleton fields map to columns on
`todo_item` (`description`, `detail_html`, `assignee_id`, `due_at`). Only cost
values are rows (`item_cost_value`), because only cost is many-per-list. Recording
this here keeps the decision from being made by accident later.

## Consequences

- The invariants are unrepresentable rather than merely unreachable, which is the
  habit `ux_app_user_single_admin` already established. A hand-run `UPDATE` cannot
  produce a list with two assignee fields or a detail field with a points rate.
- Adding a sixth kind is a migration that widens one `CHECK`, plus a frontend
  input. Flyway migrations are routine here, so this is cheap.
- Reading a list's configuration is one query returning a handful of rows, ordered
  by `position`, instead of parsing a document. Changing one field's `required` flag
  is an `UPDATE` of one row.
- The merge-on-id rule is the sharp edge: it is more code than a delete-and-insert
  and it must exist before items ship. It is specified in design/0004 and covered
  by an integration test asserting that a renamed choice keeps its id.
- Nothing stops a list from declaring a cost field with points and no items, or
  five cost fields. Both are the owner's business.

## Alternatives considered

- **A JSONB `field_config` column on `todo_list`.** One column, no joins, trivially
  versioned with the list. Rejected because every rule becomes application code: a
  bad migration, a hand-run `UPDATE` or a second writer can store a cost
  configuration on a due-date field and nothing notices until an item form crashes.
  The project's standing habit is the opposite, and JSONB also gives up ordinary
  foreign keys - `item_cost_value` could not reference a field inside a document,
  so item values would have to key off a position or a name that renaming breaks.
- **Boolean columns on `todo_list`** (`detail_enabled`, `detail_required`, ...).
  Fine for the four singletons and impossible for R22: multiple named cost fields
  are rows whatever else happens. It would also leave the cost configuration as
  nullable columns meaningful only in combination, and no place to put `position`.
- **A generic EAV `item_value (item_id, field_id, text_value, number_value, ...)`.**
  Needed if fields were user-defined with arbitrary types. They are not - the kinds
  are closed - so EAV would trade typed columns, foreign keys and `NOT NULL` for
  flexibility nothing asks for. Assignee as a real `REFERENCES app_user` is worth
  more than uniformity.
- **A field-template table shared across lists.** Normalises the four singleton
  kinds, which are already an enum in code. Adds a join to answer "what shape is
  this list" and buys nothing, since the kinds cannot vary per deployment.
