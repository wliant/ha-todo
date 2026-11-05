# 0010 - Per-list access instead of household scoping

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R19, R20

## Context

design/0001 pre-committed to household-scoped authorization: "Every read and write
will be checked against the caller's household membership in the service layer."
requirements/0001 R6 made household isolation a requirement, and left an open
question - how a member joins a household - that it said blocked R6.

The open question never had a good answer because it was the wrong question. This
is one family's own deployment, standing behind one HTTPS front door with one admin
account configured from the environment. There is no second household to isolate
from, and nothing for a member to join: being a row in `app_user` is already being
in the household. A `household` table would have exactly one row in every
deployment that will ever exist, and `household_id` would be a column with one
value, carried through every query and every test to prove a property that is true
by construction.

Meanwhile the thing a family actually needs is the opposite of isolation. "Everyone
sees every list" cannot express a private workout log, a parent's budget, or a
surprise-party list, and all three are ordinary household lists. R19 asks for
sharing to be a decision per list.

## Decision

**There is no household entity, and none will be created.** The deployment is the
household.

**Authorization is a per-list grant.** A list has an `owner_id` and a set of
`list_access (list_id, user_id)` rows. Two predicates, both evaluated in the
service layer:

- `readable` - the caller owns the list, or has a `list_access` row for it. Grants
  the right to read the list and to read, write, complete and assign its items.
  Every member with access is a peer, as requirements/0001 always said.
- `managed` - the caller owns the list. Grants the right to rename it, change its
  icon, description, field configuration and access list, and delete it.

The owner is given a `list_access` row inside the create transaction, so every
membership question is one table and one index rather than a column check OR'd with
a join. The owner's row cannot be removed.

`managed` is deliberately **not** `owner || isAdmin()`. The admin administers
accounts (requirements/0002 R9); a list is not an account. An admin who could read
any list would make "my private list" mean "private from everyone but whoever holds
the admin password", which is precisely what R19 exists to prevent.

**A member who lacks access gets 404, not 403.** R19 says an ungranted list cannot
be discovered to exist, and a 403 discloses that the id is real. A member who has
access but does not own the list gets **403** on a management route, because they
can already see the list and a 404 there would be a lie their UI would have to
work around.

The standing rule from design/0001 is unchanged in substance: a list id in a URL is
never proof of access, the check lives in the service layer and not the controller,
and the UI hiding a list is not a control. Only the thing being checked changed.

## Consequences

- requirements/0001 R6 is satisfied vacuously and its blocking open question is
  closed. CLAUDE.md's "Authorization is household-scoped" paragraph and
  design/0001's equivalent are rewritten to describe the grant.
- A private list is genuinely private from other members, which is new and is the
  feature. It is not private from anyone who can reach the deployment, because the
  roster is public and passwordless accounts exist - recorded as an accepted
  consequence in requirements/0004 rather than implied to be more than it is.
- Every list query must be filtered **in SQL**, by joining `list_access`, not
  fetched and filtered in Java. A `readable()` predicate applied after a full fetch
  passes every unit test and leaks every list, so the integration suite asserts the
  negative cases against real Postgres.
- Deleting a user cascades to the lists they own. There is no transfer, so a list
  outlives its owner only if someone else recreates it. Accepted; R20 says
  ownership is not transferable.
- There is no administrative recovery for a list whose owner will not share it.
  Accepted explicitly in requirements/0004.
- Adding a second household later would be a schema change and a rewrite of both
  predicates. That is the cost of not building it now, and it is the right trade:
  the alternative is carrying a one-valued column forever against a need nobody has
  articulated.

## Alternatives considered

- **A `household` table with one row, plus per-list access on top.** Both layers,
  so both costs: a column that never varies *and* the grant table that does the
  real work. The household check would be unfalsifiable - it can never fail - which
  makes it a check nobody maintains and everybody trusts.
- **Household scoping alone, as design/0001 planned.** Cannot express a private
  list, so it fails R19 outright. This is what forced the decision.
- **Access by role on the list** (owner / editor / viewer). More expressive than a
  binary grant, and nothing in requirements/0004 asks for it. A viewer who cannot
  tick off a chore is not a household member; R20 makes everyone with access a
  peer. Rejected as speculative.
- **`403` for an ungranted list.** Simpler and conventional, and it confirms the
  id exists. R19 is explicit that existence is itself the thing being hidden.
- **Spring Security method annotations** (`@PreAuthorize`) for the predicates. Needs
  `@EnableMethodSecurity`, moves the rule into a SpEL string, and splits
  authorization between annotations and the services that already own the
  transaction. The project has no method security today and the check needs a
  repository lookup either way.
