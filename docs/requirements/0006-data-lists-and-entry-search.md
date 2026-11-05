# 0006 - Data lists and entry search

Status: accepted
Date: 2026-11-05
Refines: [0004](0004-lists-items-and-personal-settings.md) R18 (a list is a
named thing with a face), R19 (per-list access), R21 (the detail field)
Amends: [0004](0004-lists-items-and-personal-settings.md) - its premise that a
list is a list of work, and its R31 - a shared link may now land somewhere that
is not a todo list.

## Problem

Everything in the product is work. An item is assigned, dated, completed and
scored; a list declares a due date whether its owner wanted one or not (0005
R40); the database computes whether a row is done (0005 R49). That is the right
shape for bins and vitamins, and it is the wrong shape for the other half of
what a household writes down.

**A household accumulates things it wants to find again.** The recipe that
worked. The restaurant somebody recommended in a car park. The line from a book.
The word a child asked about. None of these are ever finished, nobody is
assigned one, and they have no due date - but they are exactly the notes that get
lost, because the only place to put them today is a todo list whose items are
never ticked. A member who does that gets a due-date input they will never use,
a completion button that means nothing, and a row the dashboard will offer them
tomorrow morning.

**What makes such a note useful is finding it, and nothing in the product can.**
A list's search matches the list's *name* and no more. An item is found by
scrolling. That is survivable for twelve chores and useless for sixty recipes:
the information is in the detail field - the ingredient three bullets down, the
cuisine mentioned once - and the detail field is, as far as search is concerned,
invisible. A collection you cannot search is a drawer you cannot open.

**And the grouping is per-collection, not global.** "Recipes" and "Vocabulary"
have nothing to do with each other, but "where did I write that down" is a
question that does not know which of them to ask. So search has to work inside
one collection and across all of them, and a result has to say which one it came
from or the answer is incomplete.

None of this is an open question 0004 left. It is a premise 0004 never
examined: that a list is a list of work. This document does not widen the list
to cover both - it adds a second kind of list, because a thing that is sometimes
work and sometimes not would make every item carry fields that are meaningless
half the time.

## Who uses it

- **Members**, on phones, looking something up in the moment - in a supermarket,
  at a stove, or in front of somebody who just asked for a restaurant. They are
  reading far more often than writing, and they are searching rather than
  browsing, because they already know roughly what they want and not where they
  put it.
- **The member who writes the entry**, often at a desk and often once: pasting a
  recipe in, photographing a label, typing out the quote. They want the detail
  field they already know from items.
- **The collection's owner**, with one extra concern, the same one a list owner
  has: who else can see it. A shared Recipes collection and a private Vocabulary
  log are both ordinary.

## Requirements

### Must have

- **R50 A collection keeps what the household wants to find again.** A **data
  list** has a name, a description and an icon, and belongs to the member who
  created it, who grants access to others exactly as on a todo list (R19). Its
  contents are never assigned, never dated, never completed and never scored,
  and nothing creates them on a schedule. A recipe is not work, and a product
  that treats it as work will keep asking when it is due.
- **R51 An entry is a name, a description, and a detail.** The **name** is what
  the entry is called and is required; the **description** is one line about it
  and is not. The **detail** is the same rich text an item's detail is (R21,
  0005 R37) - bullets, numbered lists and embedded pictures - written in the same
  editor, because an ingredients list and a set of directions are the two things
  every one of these collections turns out to need.
- **R52 An entry carries tags.** Free-form, several per entry, and offered back
  from what the collection already uses as the member types. A household
  converges on its own words - "quick", "thai", "noun" - and a fixed vocabulary
  handed down by the product would be wrong for every collection after the
  first. Tags are suggested, never required.
- **R53 Search reads the whole entry.** The name, the description, the detail's
  text and the tags, all of it. A recipe whose only mention of an ingredient is
  a bullet three lines down is still found by that ingredient, because that is
  precisely the kind of thing a member is searching for and precisely what they
  cannot remember the title of.
- **R54 Search reads across every collection at once.** One search answers
  "where did I write that down" over every collection the member can see, and
  every result says which collection it came from. A member with four
  collections should not have to guess which one to open first, and a search
  that silently covered only the collection they happen to be looking at would
  teach them to distrust it.
- **R55 Search finds a word by its middle, not only by its start.** Typing
  "avi" finds "avian", and so does typing "vian". A vocabulary collection in
  which a word can only be found by its first letters is a dictionary, and the
  reason to have the collection is that the member does not know the spelling.
- **R56 Tags narrow what is shown.** A member filters a collection by one or
  several tags, and several tags mean *all* of them - "thai and quick", not
  "thai or quick" - because a filter a member adds to should give them less to
  read, not more. Filtering and searching compose.
- **R57 An entry is collapsed or expanded, and only one at a time.** Collapsed
  shows the name, the description and the tags; expanded adds the detail.
  Expanding one collapses whichever was expanded, for the reason 0005 R38 gives
  for items: a collection of recipes with pictures in them is otherwise a wall
  on a phone. Entries are ordered by name, because a collection is read by
  looking things up rather than by recency.
- **R58 Collections are their own place in the menu.** Not a tab on a todo list
  and not a kind of list mixed into the list of lists. The two are different
  enough that a member choosing where to put something should not have to read
  carefully, and a surface that shows both would have to explain the difference
  every time.
- **R59 A shared link may become an entry.** The share target (R31, adr/0020)
  offers collections alongside todo lists, with the shared title becoming the
  entry's name and the link living in its detail. Restaurants and recipes mostly
  arrive as links, which is the case the share target was built for, and
  extending its picker is cheaper than a second way in.
- **R60 Anybody with access is a peer on the entries.** Any member who can see a
  collection may add, edit and delete any entry on it - the rule items already
  follow (R30) - while only the owner changes the collection itself and who can
  see it (R19). Two members editing one entry at the same time is told about
  rather than silently resolved, as on an item (R30): the second writer is shown
  what the entry actually says now and nothing is retried behind their back.
- **R61 An entry's pictures are as private as its collection.** A picture
  embedded in an entry's detail is readable by exactly the people the collection
  is readable by, and by nobody else - the rule adr/0019 states for an item's
  detail, applied to a third kind of owner. A private collection's photographs
  are as private as the collection.

### Explicitly not required

- Manual ordering of entries, or drag-and-drop. They sort by name (R57).
  Nothing in a collection has a natural sequence the way a list of chores does.
- Pinning a collection, or a recent-collections section in the sidebar. The
  menu entry (R58) and search (R54) are the two ways in; a collection is opened
  far less often than a todo list and almost always via search.
- A per-collection field configuration, as todo lists have (R21). Recipes would
  want ingredients and a time, restaurants a cuisine and a rating - and asking a
  member to design a schema before adding their first restaurant is how the
  collection stays empty. Tags (R52) and the detail (R51) carry that structure
  instead.
- Anything that treats an entry as work: assignment, due dates, completion,
  points, recurrence, or appearance on the dashboard (0005 R44). This is the
  distinction the whole feature rests on.
- Nested collections, collection templates, archiving, or comments on entries.
  Unchanged from 0004 and 0005.
- Export, import, or printing. A collection is read in the app.
- Ranking by what the member looks at most, or any other personalisation of
  search results. Results rank by how well they match and nothing else.
- Offline use. Still out of scope from 0001, and more tempting here than
  anywhere else in the product - a recipe is wanted in a kitchen - which is why
  it is worth saying no to explicitly rather than by omission.

## Consequences accepted deliberately

1. **There are now two kinds of list, and the product has to say which is
   which.** Every screen that offers a choice of destination - the share target
   most of all (R59) - has to present both and make the difference legible
   without a sentence of explanation. A member who puts a recipe on a todo list
   by mistake gets a due-date field and a completion button, and nothing moves
   it for them. Accepted because the alternative, one list that is sometimes
   work and sometimes not, means every item carries fields that are meaningless
   half the time.
2. **Search is the first thing in the product the database does cleverly.**
   R53 and R55 cannot be answered by the kind of query the rest of the product
   uses, so the collection's searchable form is maintained alongside its content
   and is indexed. That is a second representation of the same text, and the two
   can only disagree if code lets them - so the design's job is to make
   disagreement unrepresentable rather than to check for it.
3. **A tag is an exact word, and that is visible to members.** Tags are matched
   as written rather than stemmed, so "dessert" and "desserts" are two tags. The
   suggestions in R52 are what keeps a collection from accumulating both, and
   they will not always succeed. Accepted because a tag whose meaning the
   product interprets is a tag a member cannot rely on.
4. **Fuzzy matching will sometimes offer something unwanted.** R55 means a
   short query can match a word the member was not thinking of. Those matches
   rank below exact ones, always, so the cost is a longer result list rather
   than a wrong first answer - but a member who types three letters will see
   results they did not predict.
5. **A collection has no recency, so one share-target affordance is weaker for
   it.** The share picker preselects the todo list a member touched most
   recently (adr/0020); collections are not tracked that way, so they are
   offered in alphabetical order and never preselected. Sharing to a collection
   is therefore one tap more than sharing to a list. Accepted rather than
   fixed, because per-member recency on a second kind of list is a table and a
   write on every read, for a saving of one tap.
6. **Pictures now have a third kind of owner.** R61 means the question "who may
   read this picture" has three answers to check instead of two, and every
   future owner adds a fourth. The rule stays in one place, but the shape does
   not scale indefinitely and the design should say at what point it is
   replaced rather than extended again.
7. **The detail editor is now loaded by two features.** R51 reuses the item's
   editor, which is deliberately not in the main bundle because most lists never
   ask for it. Collections ask for it almost always, so a member who lives in
   their recipes pays that download on nearly every visit. Accepted: it is the
   same editor, and two implementations of a rich text field would diverge in a
   month.

## Open questions

- Whether a collection should be able to hold something a member has *not* yet
  decided is a recipe - an entry with only a link in it, filed nowhere. R59
  makes a shared link an entry with a name, which means the member names it on
  the way in. Whether that is one step too many is worth watching rather than
  guessing.
- Whether entries should be movable between collections. A restaurant that turns
  out to be a recipe source is plausible; so is this never coming up. Not built,
  because a move between two collections with different tag vocabularies raises
  a question about the tags that nothing yet answers.
- Whether search should offer a snippet showing *where* in the entry it matched.
  R54 reports which collection a hit came from, which is the part a member
  cannot work out for themselves; the matching line inside a long detail is the
  next thing they would want and is a larger piece of work than it looks.
- Whether points can be revoked by a list's owner. Unchanged from 0005, and
  untouched by this document - collections have no points.
- Whether a shared list should show who else has it open. Unchanged from 0004
  and 0005, and now true of collections too.
