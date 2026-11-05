# 0007 - Calendar feeds on My day

Status: accepted
Date: 2026-11-06
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) R44,
R45 (the dashboard reads across lists and is the landing page), R46 (the week
and month views)
Amends: [0005](0005-completion-detail-recurrence-and-planning-views.md) - its
premise that everything on My day is an item of this product's own making.

## Problem

My day answers "what does this household owe today" and it is wrong about
today. A birthday dinner at seven, a parents' evening, the car being serviced,
one member away on Thursday - none of that is in this product, all of it decides
whether anybody can take the chore that is. A member looking at My day sees the
work and not the day the work has to fit into, which makes the one screen the
product tells them to start from the one screen that cannot be trusted.

**The events already exist, and not here.** Everybody in this household keeps
their appointments in Google Calendar or Outlook, because that is where the
invitations arrive and that is what their phone notifies them from. Nobody is
going to type an appointment twice. Any feature that begins "add an event" has
already lost: it would hold a worse copy of a calendar that nobody maintains,
and the first time the real calendar and this one disagreed, members would stop
reading this one.

**What is missing is not storage, it is the join.** Google Calendar can already
share a calendar with another household member in two taps - and it will never
show that member's chores next to it, because it does not know this product
exists. This product does. The thing neither side can do alone is put an
appointment and a due chore in the same column of the same day, which is
exactly the question a household asks each morning. That join is the entire
value of this feature, and every requirement below is subordinate to it.

**And the awareness is specifically of other people.** A member who only sees
their own events has gained nothing over the phone in their pocket. The feature
exists so that Ana can see that Ben is out on Thursday evening before she claims
Thursday's cooking - so visibility across members is the point rather than a
setting, and the question it raises, "how much of my calendar does the household
get to read", has to be answered deliberately and visibly rather than by a
default nobody reads.

This is not a calendar application. There is no Calendar in the menu, nothing
creates an event, and nothing here is ever assigned, completed or scored.

## Who uses it

- **Every member, on a phone, on My day.** They are reading, in the morning or
  in passing, and they want one answer: what is happening and what needs doing.
  They will not configure anything to get it, and they will not visit a second
  screen for the other half of the day.
- **The member subscribing a calendar**, once, probably at a desk, probably
  grumbling: getting a feed address out of Google or Outlook is a trip through
  somebody else's settings screen. This happens a handful of times in the life
  of the deployment, so it is allowed to be a little laborious - but it must be
  obvious afterwards that it worked, and obvious what the household can now
  read.
- **The member deciding what to expose.** Some calendars are shareable and some
  are not, and the same person owns both. They need to see what they are about
  to publish before they publish it, and to take it back in one action.

## Requirements

### Must have

- **R62 A member subscribes a calendar by its feed address.** A **calendar
  feed** is an iCalendar (`.ics`) URL - Google's "secret address in iCal
  format", Outlook's published calendar link - with a label the member chooses,
  and it belongs to the member who added it. Several per member, because a work
  calendar and a family calendar are two feeds. Which calendar is synced is
  decided by which address the member pastes; the product offers no picker,
  because it never holds an account.
- **R63 Nothing in this product ever creates, edits or deletes an event.** The
  feed is the truth and this is a copy of it. There is no new-event form, no
  editing of a synced event, and no writing back to Google or Outlook. A second
  place to author an appointment is a second place for it to be wrong.
- **R64 A feed is visible to the members its owner chooses.** The owner grants
  access per feed, the way a list's owner grants access to a list (0004 R19),
  and a feed starts visible to nobody but its owner. Events from a feed are
  readable by exactly the members holding a grant on it, and by nobody else.
  Access is checked on every read, not only on the screen that shows it.
- **R65 The member sees what the household will see, before it is saved.** On
  adding a feed the product fetches it immediately and shows what came back -
  the next several events as the household will read them - and saves nothing
  until the member confirms. The feed address is unguessable but the calendar
  behind it may be the member's whole life; a mispaste that publishes every work
  meeting and medical appointment must be caught at the one moment the member is
  paying attention.
- **R66 A feed's address is a secret, and is never readable back.** Anybody
  holding the address can read that calendar, forever, without signing in to
  anything. It is written and replaced, never returned - not to the household,
  not to an administrator, and not to the member who typed it. The screen shows
  the label, the provider and when it last synced.
- **R67 Events appear on My day, merged with the work.** In the day, week and
  month views (0005 R44, R46), in one chronological sequence with items rather
  than in a section of their own: an event and a due chore are peers on the same
  day, because the question is "what is Thursday like", not "what are my chores
  and separately what are my appointments". An event says whose calendar it came
  from, and reads differently enough from an item that nobody mistakes one for
  the other.
- **R68 An event opens to say what it is.** Its title, when it is, where it is,
  whatever description the calendar carries, and which member's feed it came
  from. Read-only - there is nothing to do to an event - and the same one-at-a-
  time popup an item opens into (0005 R38), because My day already works that
  way.
- **R69 One event seen twice is shown once.** When two members' feeds both carry
  the same appointment - because one invited the other - it appears as a single
  entry naming both, not as two rows. A household calendar that double-counts a
  dinner is a calendar members learn to discount.
- **R70 The household has one timezone, and an all-day event has no time.**
  Days, weeks and months are bounded in the household's configured zone for
  every member and every device, so two members never disagree about what is on
  Thursday. An event marked all-day is shown as all-day on its date, never as
  midnight.
- **R71 A feed that has stopped working says so, where it matters.** Feeds are
  refreshed on a schedule and a refresh can fail - a regenerated address, a
  provider outage, a deleted calendar. The product records when each feed last
  succeeded, shows it to the owner, and tells members on My day when a feed they
  can see has gone stale. Missing events that nobody is told about is the one
  failure this feature cannot be allowed to have: the member reads an empty
  Thursday and believes it.
- **R72 Taking a feed back takes its events with it.** Deleting a feed, or
  revoking a member's grant on it, removes those events from that member's view
  at once. Nothing survives as a snapshot of a calendar the household is no
  longer entitled to read.

### Explicitly not required

- Manual event entry, editing, or writing anything back to Google or Outlook.
  R63 is the whole position, and it is what keeps this from being a calendar
  application with two sources of truth.
- A Calendar menu entry, member-created calendars, or any screen whose subject
  is a calendar. Feeds are configured in a member's profile, next to the other
  thing that is theirs alone, and the events are read on My day. A second
  navigation surface for a read-only strip is how the product grows a feature
  nobody opens.
- OAuth connections to Google or Microsoft, a calendar picker, or anything that
  requires an application registration, a consent screen or a stored refresh
  token. See [adr/0034](../adr/0034-calendar-feeds-are-read-only-ics-subscriptions.md);
  the cost is a feed that lags its source, which is accepted below.
- Reminders, notifications, or push of any kind. Members already get those from
  the calendar the event lives in, which is better at it and is on their phone.
- Attendee lists and organiser addresses. They are mostly e-mail addresses of
  people outside the household, they answer no question anybody asks on My day,
  and they are the part of an event a member would least expect to have
  published.
- Busy-only or title-only privacy modes inside this product. The member chooses
  what to expose by choosing which calendar to publish and - on Outlook - what
  detail level to publish it at, and R65 shows them the result. A second,
  weaker privacy control here would mostly teach members to stop curating the
  calendar, which is the control that actually works.
- Free/busy reasoning, scheduling, conflict detection, or "who is available".
  The product shows the day; the household reads it.
- History. Events are kept only for the window My day can show, so this can
  never answer "what did we do last month".
- Appearance anywhere other than My day - not in the sidebar, not on a list, not
  in search. Collections' search (0006 R54) does not read events.
- Timezone-per-member. R70 is deliberate: one household, one zone.

## Consequences accepted deliberately

1. **The household sees a copy that lags.** Providers regenerate published
   `.ics` output lazily, and Google's can lag the calendar by hours. An event
   added this morning may not be on My day until this afternoon, and a cancelled
   one may linger. Accepted, because the alternative is two OAuth application
   registrations, a sensitive-scope verification and stored refresh tokens for a
   household of four - and because the events this feature exists for are known
   days in advance, not minutes.
2. **Getting the address is the member's problem, and it is a bad five minutes.**
   Google buries it under the calendar's settings and Outlook under "Publish a
   calendar", both on a screen designed for a desktop. The product can guide but
   not shorten it, so onboarding a member is a thing somebody helps with once.
3. **The product now stores a credential-grade secret it cannot rotate.** A feed
   address is a bearer capability for somebody's whole calendar, sitting in our
   database and in every database backup. R66 keeps it out of every response,
   but anybody with database access holds it, and only the member can invalidate
   it - by regenerating the address at the provider, which silently breaks the
   feed until they paste the new one. Accepted, and it is the strongest reason
   R65 exists.
4. **One timezone becomes the whole product's timezone.** R67 merges events and
   items into one grid, and one grid cannot have two definitions of Thursday -
   so R70's household zone governs the bucketing of *items* on My day too, which
   until now followed each member's device. A member whose phone is in another
   country will see this household's Thursday rather than their own. That is the
   right answer for a household and a visible change for a traveller.
5. **A member can expose far more than they meant to, and the product cannot
   tell.** Nothing here can distinguish "my curated family calendar" from "my
   entire work life": both are a URL that parses. R65's preview and R64's
   nobody-by-default are the whole defence, and a member who clicks through the
   preview has published everything on that calendar to whoever they grant.
6. **Events are only as private as the household is trusted.** A grant is per
   feed and binary, with no detail levels (above), so sharing a feed with
   somebody means sharing every field of every event on it, including
   descriptions. In a deployment where every member is family that is the right
   trade; it is not a model that would survive the household gaining a lodger.
7. **The only way an event gets into the product is a scheduled fetch of a
   third-party URL.** That is a new kind of dependency for this product - the
   first outbound call it makes to anything - and it is why the fetch has to be
   treated as hostile input: a URL a member supplies, pointed wherever they
   like, returning bytes of whatever size, parsed by a new dependency. It also
   makes the feature awkward to exercise end-to-end against a real deployment,
   which the design has to answer rather than paper over.
8. **My day's quiet state is now ambiguous.** An empty Thursday used to mean
   "nothing is due". It now also means "nothing is due and no feed told us
   otherwise", and R71 is what keeps those two apart. If the staleness notice is
   ever quietly dropped from the UI, the feature degrades into lying.

## Open questions

- Whether the stored feed address should be encrypted at rest. It would have to
  be with a key from the same environment file that holds the database password,
  which is honest about key management rather than good at it, and buys little
  against the threat that matters (database access). Left plain and documented
  rather than encrypted theatrically - worth revisiting the day this product
  stores a secret that matters more.
- Whether a feed should be shareable by anyone other than its owner, or whether
  the admin should be able to see which feeds exist. The admin administers
  accounts, not contents (adr/0010), and extending that to "which calendars Ben
  publishes" has not been needed.
- Whether My day should be navigable - previous and next week, previous and next
  month. The views are fixed to the current period today, which is a smaller
  limitation when the only content is this household's own chores than it is
  once real appointments are in there. Not part of this document, but this is
  the feature that will make somebody ask.
- Whether a declined invitation should be hidden. The feed says whether the
  owner declined, and showing a declined event as if it were happening is
  mildly wrong; hiding it is a judgement the product would be making about
  somebody's calendar. Shown for now.
- Whether earned points can be revoked by a list's owner. Unchanged from 0005
  and 0006.
- Whether a shared list should show who else has it open. Unchanged from 0004,
  0005 and 0006.
