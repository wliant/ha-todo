# 0036 - The household has one timezone, and the client is told what it is

Status: accepted
Date: 2026-11-06

## Context

`requirements/0007` R67 merges calendar events into My day's existing day, week
and month views, and R70 says the household has one timezone and that an all-day
event has no time.

Today My day is bounded by the *browser's* zone. `web/src/dashboard/calendar.ts`
computes start-of-day, start-of-week and the month grid with `setHours` and
`setDate` on a local `Date`, and `dayKey` deliberately avoids
`toISOString().slice(0, 10)` because that would be UTC's day rather than the
member's. The window is then sent to `/api/dashboard/scheduled` as two instants,
and the server does not care which zone produced them. For a household in one
place this has been invisible and correct.

Two things make it insufficient now.

**One grid cannot have two definitions of Thursday.** If an item is bucketed by
the device's midnight and an event by the household's, the merged row for
Thursday is a different Thursday for each, and a late-evening chore and a
late-evening appointment can land in different columns of the same view.

**An all-day event is not an instant.** RFC 5545 writes it as a bare date with
no zone - `DTSTART;VALUE=DATE:20261107` - and anything that resolves it to an
instant has to choose a midnight to resolve it against. Parsers choose the JVM
default, which in every container image here is UTC, so an all-day event
rendered in a zone ahead of UTC appears on the previous day, in the evening.
This is not a rendering detail that can be fixed in the view: the choice is made
at parse time.

The product already has the required configuration.
`app.recurrence.zone` (`APP_TIMEZONE`, defaulting to `UTC`) exists so that "the
bins go out at 7pm" means 7pm where the household is (`adr/0025`).

## Decision

**One zone for the household, named `app.timezone`, and it governs every
date-to-day decision in the product.**

- The property moves up to `app.timezone` (`APP_TIMEZONE`), defaulting to
  **`Asia/Singapore`**, and `app.recurrence.zone` is removed - the recurrence
  job reads the same property. One household cannot have two zones, and a
  deployment that sets one and forgets the other would generate chores at the
  right hour and show them on the wrong day.
- The server exposes it to the client as part of the session's own data, so the
  SPA never guesses and never reads `Intl.DateTimeFormat().resolvedOptions()`.
- `calendar.ts` takes the zone as an argument and computes day boundaries in it,
  using `Intl.DateTimeFormat` with a `timeZone` to read the parts of a date - no
  date library, as the module's own note argues, and no arithmetic on
  milliseconds.
- A timed event is stored as an instant, resolved at parse time from the feed's
  own `TZID` where it has one and from `app.timezone` where it does not
  (RFC 5545 floating time).
- An all-day event is stored as a **date**, never an instant, with a flag, and
  is placed by that date and rendered without a time.

## Consequences

- **Items and events agree about what day it is**, which is what makes R67's
  merged grid coherent at all.
- **An all-day event lands on its own date** in every zone, because it was never
  converted.
- **My day changes for a member whose device is elsewhere.** A traveller sees the
  household's Thursday, not theirs. That is correct for the question My day
  answers - "can somebody here do this on Thursday" - and it is a visible
  behaviour change to an existing screen, made deliberately.
- **`calendar.ts`'s functions all gain a zone parameter**, and the hazard moves
  rather than disappearing: a new helper that quietly uses the device zone will
  work perfectly in Singapore and be wrong for exactly one member. The module's
  existing commentary is the right place to say so.
- **The default changes from UTC to `Asia/Singapore`.** That is where this
  household is; a committed default of UTC was a placeholder that only looked
  neutral. Every deployed environment already sets `APP_TIMEZONE` explicitly, so
  nothing moves under them.
- **Per-member timezones are now a schema change away rather than a flag away**,
  which is the right cost for something 0007 explicitly does not want.

## Alternatives considered

**Keep bucketing by the browser's zone and convert events into it.** Simplest,
and wrong twice: all-day events have no instant to convert, and two members in
different zones would see different days - in a product whose entire purpose is
that they agree.

**Let each member choose their own zone in preferences.** More flexible,
immediately contradictory: Ana's Thursday and Ben's Thursday would be different
rows of the same shared household, and "who can cook on Thursday" stops having
one answer.

**Store all-day events as midnight UTC and special-case the rendering.** This is
the bug, written down as a design. Every reader would have to remember that one
column's instants mean a date, and the one that forgets shows the school play on
the wrong evening.
