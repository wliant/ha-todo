# 0019 - Numbers a household keeps

Status: accepted
Date: 2026-11-19
Amends: [0001](0001-shared-household-todo.md) and
[0006](0006-data-lists-and-entry-search.md) - their shared premise that a
household writes down either *work* or *things to look up*.

## Problem

The product holds two kinds of thing and neither is a number.

A **list** holds work: an item is assigned, dated, completed, scored, and
leaves the agenda when it is done. A **collection** holds something to find
again: an entry is named, described, tagged and searched. Both are shaped
around a thing that is finished or filed.

**A household also measures.** What somebody weighs. What the groceries cost
this month. How long a child read today. These are not work - nobody is
assigned a weight and a weight is never completed - and they are not reference,
because there is no document to search; the whole content is one number. They
are the readings a household takes, and the reason to take them is that a
single one says almost nothing: the shape they make over months is the entire
point.

**Today that lives on paper, or in a note that is appended to until it is
unreadable.** The nearest thing the product offers is a collection entry whose
detail field holds a list of numbers, which cannot be charted, cannot be
summed, cannot be compared between two members, and is typed as prose every
time. A member who tries it gets a search index over a column of digits.

**And the two obvious shortcuts are both wrong.** A tracker is not a todo list
with a numeric cost: a cost is a property of a chore that got done, so a weight
would need a chore to hang on, and `TodoListService.create` would give it a due
date and a completion it can never use. A tracker is not a collection with
numeric entries either: `data_entry`'s generated `search_vector` and its tags
exist to find text, and a tracker has no text to find. Both shortcuts put a
number inside a thing shaped for something else, which is how the number ends
up unreadable again.

## Who uses it

- **The member who weighs themselves in the morning.** One number, every day,
  on a phone, with one hand. The gesture has to cost a tap or it stops
  happening by Thursday - and if it stops happening the data is worthless, so
  this is the requirement everything else bends around.
- **The member who watches what the household spends.** They enter a figure
  after each shop, and what they want to see is the month, not the receipts.
  They are the reason several numbers can land in one bucket.
- **Two members measuring the same thing.** Both weigh themselves; neither
  wants their weight averaged with the other's. They want two lines on one
  chart.
- **The member who shares none of it.** A tracker granted to nobody is private,
  exactly as a list granted to nobody is private. There is no second privacy
  mechanism to learn.

## Requirements

### Must have

- **R159 A tracker declares its shape when it is created**: a name, an icon, a
  **unit** (`kg`, `SGD`, `minutes`), a **period** (day, week or month), and a
  **rollup** (sum, average or latest). The unit is a label and never a
  conversion - the product does no arithmetic between units, and two units are
  two trackers.
- **R160 A tracker declares whose numbers it holds**: `PER_MEMBER`, where
  everyone with access keeps a series of their own, or `SHARED`, where there is
  one series the household writes together. Chosen at creation.
- **R161 Period and scope are fixed once the tracker holds a reading.**
  Changing a month tracker to a day tracker strands every stored bucket at a
  boundary that no longer means anything, and changing per-member to shared
  merges four people's weights into one line. The name, icon, description,
  unit, precision and target stay editable.
- **R162 A reading is one number, in one bucket, with an optional note.** The
  form asks for the bucket the tracker declared - a day for a daily tracker, a
  month for a monthly one - and nothing else. The note is plain text.
- **R163 Several readings may share a bucket, and the rollup folds them.**
  Three shopping trips in November sum to November. Two weigh-ins in a day
  resolve by the rule the tracker declared. This is why a rollup is declared at
  all, and it is also what lets a long range be drawn coarsely: a year of a
  daily tracker is folded into months by the same rule.
- **R164 Access to a tracker is access to its numbers.** Everyone with a grant
  reads every series on it, and the chart overlays them. Privacy is the grant,
  as it has always been: a tracker shared with nobody is seen by nobody.
- **R165 Recording needs access; correcting depends on whose number it is.**
  Any member with access may record. On a `PER_MEMBER` tracker a member edits
  and deletes **only their own** series. On a `SHARED` tracker anyone with
  access may correct any number, because the series belongs to the household.
- **R166 Buckets are the household's.** A day, a week and a month are reckoned
  in `app.timezone` (0007 R64, adr/0036), server-side, whatever device recorded
  the number. A weight entered at 23:30 belongs to that evening's day.
- **R167 A tracker's screen shows the shape, then the numbers**: a chart over
  the chosen range, a headline figure, and the readings underneath, newest
  first. A bucket nobody recorded is a **gap**, drawn as a break - never a
  zero, because a missed weigh-in is not 0 kg.
- **R168 A tracker may declare a target** - a figure with a direction (at most,
  at least). It is drawn on the chart and the headline says how far off it is.
- **R169 The range is on screen in the address**, as the agenda's period and a
  list's filter are (adr/0037, adr/0045), so a view is a link and a reload
  keeps it.
- **R170 Trackers get the grant model, the pins and the sidebar group the other
  two kinds have** - the same sharing screen, the same star, the same group
  ordered by how often a member opens each one (0012 R98-R106, adr/0043).
- **R171 The agenda says when a daily tracker has nothing recorded today**, as
  one quiet strip beside the existing notices, with a control that records from
  there. Daily trackers only, today only.
- **R172 Deleting a member deletes their own series and keeps the
  household's.** Their per-member readings go with them; numbers they recorded
  into a shared tracker stay, with the authorship dropped.

### Explicitly not required

- **Pictures on a reading.** A note is plain text. `stored_image` carries three
  nullable owner columns under one check, and adr/0032 sets the threshold for
  replacing that shape with a join table at a **fourth** owner - a number does
  not need pictures enough to spend that.
- **Rich text anywhere in this feature.** No editor, no sanitizer, no claim
  pipeline.
- **Assignment, completion, points, recurrence.** A reading is not work. It is
  never due, never done, never worth anything, and no scheduled job writes one.
- **A reading on the agenda, beyond R171's nudge.** A number is not a chore,
  and a planning surface that mixed the two would have to answer what it means
  to complete a weight.
- **Arithmetic between trackers**, and unit conversion of any kind. Minutes
  plus SGD is a bug with a plausible UI.
- **A general field declaration.** A tracker holds one number, not a per-list
  shape like `list_field`. The day a tracker needs two numbers it is two
  trackers, and if that stops being true it is a new requirement, not a column.
- **Importing or exporting.** Reading a CSV in is a different feature with a
  different failure mode.

## Consequences accepted deliberately

1. **A third aggregate is a third copy of the access predicate** - owner or
   grant, 404 when absent, 403 when ungranted-but-visible. adr/0029 accepted
   that cost for the second kind on the grounds that sharing the implementation
   is what couples the features; it is accepted again here, and a *fourth* kind
   should re-examine the trade rather than copy it a fourth time.
2. **"Every list a member can see" now has three answers.** Anything written
   from here that reads across a member's things has to say which of the three
   it means - and the schema is what stops it getting that wrong silently.
3. **The sidebar grows a third group**, and the app makes three sidebar
   requests on a cold load rather than two.
4. **The app gains a charting dependency**, the heaviest in its dependency
   list. It is loaded only by the screen that draws a chart.
5. **A tracker with no readings can still be reconfigured**, so a member who
   picks the wrong period has a way out - until the first number lands. After
   that R161 means starting again, which is the honest cost of not corrupting
   what is stored.

## Open questions

- **Whether a reading should be able to carry a time as well as a bucket.**
  Two weigh-ins in a day currently resolve by order of recording; a member who
  back-fills yesterday evening after this morning gets the order they typed,
  not the order it happened.
- **Whether a tracker should be able to read another tracker** - weight over
  height, spend per person. Every version of this is arithmetic between units,
  which is why it is excluded above rather than deferred.
- **Whether the nudge should learn.** R171 asks every day; a member who records
  on weekdays only will be told off on Saturdays.
