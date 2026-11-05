# 0025 - Recurrence is materialised by a scheduled job, on one instance

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R41 (standing instructions), R42 (the system creates them by itself)
Amends: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md)'s exclusion of recurring
items, by way of requirements/0005

## Context

R42 is the first behaviour in this product that is not a response to a request. Up
to now every row was written because somebody asked; a recurring item appears
because a rule said it would.

There is nothing to build on. `app/src/main` contains no `@Scheduled`, no
`@EnableScheduling`, no `TaskScheduler`, no Quartz, and `app/build.gradle` has no
scheduling dependency. The only asynchrony-adjacent machinery is Spring application
events used **synchronously** inside the publishing transaction
(`ListDeletedEvent`, `ListAccessRevokedEvent`), which is a useful pattern for
consequences but is not a clock.

Three things need deciding. What triggers generation at all - a clock, a read, or a
completion. How a second run avoids creating the same chore twice. And what the job
authenticates as, given `SecurityContextCurrentUserProvider` is built to fail rather
than fall back and there is no caller.

`adr/0003` deferred Redis and noted that a second backend instance is what would
force it. That bears directly here: a scheduler is the second thing that assumes one
instance, and it assumes it after sessions already do.

## Decision

**A `@Scheduled` job, on the assumption of exactly one instance.** No lock, no
leader election, no `shedlock`. Both compose files run one `app` service with one
replica, which makes the assumption true, and this ADR is where it stops being
undocumented.

**The schedule is environment configuration.** `app.recurrence.cron`
(`RECURRENCE_CRON`, default `0 5 1 * * *` - after midnight so "today" has turned
over, off the hour so it never shares a minute with anything else),
`app.recurrence.enabled` (`RECURRENCE_ENABLED`), and
`app.recurrence.catch-up-days` (`RECURRENCE_CATCH_UP_DAYS`, default **0**).
`@EnableScheduling` goes on the feature's own `@Configuration`, behind
`@ConditionalOnProperty`, so the wiring travels with the feature and disabling the
feature disables the scheduler with it.

**A new `app.household.zone`.** The cron expression and a rule's `due_time` are both
read in it. Without it, `(occurrence_on, due_time)` becomes an instant in the
container's zone - UTC in every Docker image - and "the bins go out at 7pm" is wrong
by the offset. `TZ` is set in no compose file today.

**Double creation is prevented twice**, mirroring design/0004's stance on `@Version`.
`last_materialised_on` is the deterministic guard: a run generates over
`[max(last_materialised_on + 1 day, today - catchUpDays), today + lead_days]` and
advances the mark in the same transaction, so a second run the same day computes an
empty range. An occurrence is **asked for before it is
inserted**, and `ux_todo_item_occurrence (rule_id, occurrence_on)` is the backstop
behind that check. **The failure mode is a log line, not a duplicate chore.**

This is a correction to how the decision was first written down. Catching the
constraint violation per occurrence cannot work: Hibernate defers the insert to
flush, so the violation arrives at commit - after the loop, attributable to no
particular occurrence, and with the session already unusable. The index still
guards two runs genuinely racing, and that case fails the rule's whole
transaction, which the runner records against the rule.

**One transaction per rule.** `RecurrenceRunner` is untransacted and loops;
`RecurrenceMaterialiser` is `@Transactional` per rule. One rule failing must not
roll back the rest, and a rule whose assignee lost access generates *unassigned*
with a `last_error` rather than throwing at all.

**The job does not pretend to be a user.** Setting a synthetic principal into
`SecurityContextHolder` is rejected: it hands a job the standing of an authenticated
subject, and anything downstream reading `requireUserId()` acts as a user who did
not ask. So the materialiser must not call `TodoItemService.create`, which calls
`requireCurrentUser()`. Instead the field-application logic is extracted into a
collaborator taking an **explicit `AppUser actor`**, and a new `createFromRule` uses
it. Authorization is settled by the data: the rule is on the list, so the list is
the authority, and `created_by` is the rule's author - a real person, because a
synthetic "system" user would be a lie in the audit trail.

**Two ad-hoc triggers.** `POST /api/lists/{id}/recurrence/runs` needs only access -
a member who just wrote a rule wants to see it work, and the result is an item they
could have added by hand. `POST /api/admin/recurrence/runs` is R42's "make it run
now" and returns **counts only**: no description, no list name, no rule id.

## Consequences

- Items appear without anybody opening the app, which is what R42 asked for and what
  neither alternative below delivers.
- **A second backend instance double-runs every rule.** The unique index turns that
  into logged violations rather than duplicate chores, so the failure is noisy and
  harmless rather than silent and wrong. And `adr/0003` already notes that in-memory
  sessions break first, so recurrence is not what blocks a second instance - whoever
  adds one has a Redis ADR to write before this becomes their problem.
- The admin's run creates items on lists the admin cannot see, which sits against
  `adr/0010`'s "the admin administers accounts, not list contents". It is defensible
  only because it reads nothing back, and the counts-only response is load-bearing,
  not a convenience. The obvious next request - a per-rule breakdown - must be
  refused.
- Missed occurrences are not created. A chore due last Tuesday does not appear on
  Friday. `catch-up-days` is a property so a household that disagrees needs no code
  change, but the default says what the product believes.
- **Integration tests must set `app.recurrence.enabled: false`**, or a Testcontainers
  run can fire the job mid-assertion and a flake appears months later with no
  obvious cause. The materialiser takes an injected `Clock` so "two runs in one day"
  and "a run after five days down" are plain unit tests.
- `createFromRule` is the one entry point in the item service that does not consult
  `CurrentUserProvider`. It needs a comment saying why, and a test that injects a
  throwing provider, or somebody will "fix" it by setting a security context.
- A scheduled job in a product with no monitoring fails silently, which is the whole
  risk of R42 - hence `last_run_at` and `last_error` on the rule, so the Recurring
  tab can say why a rule produced nothing.
- Recurrence expressiveness is capped at daily, chosen weekdays, and a day of the
  month, each with an interval. "Last Friday of the month" needs a fourth frequency
  and two columns, not a parser.

## Alternatives considered

- **Materialise lazily, on read.** When a list or the dashboard is read, generate
  what is now due. No scheduler, no new infrastructure, and correct after arbitrary
  downtime. Rejected because a `GET` would perform writes - which breaks caching,
  makes a read contend on the list, and means two members opening a list race to
  create the same chore - and because an item that only exists once somebody looks is
  not what R42 describes.
- **Generate the successor when the previous occurrence is completed.** Simplest of
  all, no clock, no state beyond the item itself, and it reuses the synchronous event
  pattern the codebase already has. Rejected because a skipped week stops the chain
  permanently: nobody does the bins once, and the bins never appear again. It also
  cannot express a rule independently of its items, which R41 requires.
- **Quartz, or a database-backed scheduler with locking.** Correct under any number
  of instances. Rejected as infrastructure for a problem this deployment does not
  have, on `adr/0003`'s reasoning: one household, one instance, and a unique index
  that already makes the bad case safe.
- **A host cron calling the admin endpoint.** No in-process scheduler at all, and the
  schedule becomes an operations concern. Rejected because it puts a required piece
  of the product outside the deployment the scripts manage - `scripts/deploy.sh`
  would have no way to guarantee it, and a rebuilt host would silently stop
  generating items.
- **A synthetic "system" user as the actor.** Makes `created_by` non-null and lets
  the job reuse `TodoItemService.create` unchanged. Rejected because it manufactures
  a principal, defeating the provider's deliberate fail-fast design, and because the
  honest answer to "who added this" is the person who wrote the rule.
