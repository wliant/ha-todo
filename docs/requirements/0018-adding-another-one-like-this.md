# 0018 - Adding another one like this

Status: accepted
Date: 2026-11-18
Refines: [0004](0004-lists-items-and-personal-settings.md) - whose R21 made
adding an item a blank form, and
[0010](0010-completing-from-the-agenda.md) - whose R83 made the agenda writable
in exactly one way.

## Problem

A household repeats itself. "Change the bed linen", "pay the preschool fees",
"take Mum to the clinic - bring the referral letter and the blue card" are the
same item next month as they are this month, and the part that costs something
to produce is the part that never changes: the description, and whatever was
typed into the detail field. A chore whose detail holds four lines of
instructions is a chore somebody re-types, badly, or does not add at all.

The product has two answers already and neither fits. A **standing
instruction** (0005 R41) is the right answer for a chore with a *rhythm* -
every Tuesday, the first of the month - and the wrong one for a chore that
simply happens again at no interval anybody could name. **Editing** the
existing item is worse: it destroys the record of the one that was finished,
which 0005 R36 and R43 exist to keep.

So what is missing is the smallest thing: start a new item holding the writing
of an existing one, and let the member fill in the rest. It is wanted in both
places a member looks at a chore - on the list, where they are already looking
at last month's one, and on the **agenda**, where they are looking at today and
realising this needs doing again.

## Who uses it

- **The member re-adding a chore they just finished.** They are looking at the
  struck-through row. One tap should give them a copy to date.
- **The member planning from the agenda.** They opened a chore to think about
  it and realised it needs to happen twice. They should not have to find the
  list first.
- **The member who keeps instructions in the detail field.** They are the
  reason this is about the detail as much as the description.

## Requirements

### Must have

- **R153 Every item row offers Duplicate**, beside Edit, whatever state the
  item is in. A finished chore is the one most often wanted again, so a control
  that disappeared on completion would be missing exactly when it is needed.
- **R154 Duplicate opens the add form, not the edit form.** It creates a new
  item; the original is untouched, keeps its completion, its note, its points
  and its id. The form says which of the two it is.
- **R155 A duplicate carries the description and the detail, and nothing
  else.** No due date, no assignee, no costs - those are what made the original
  *that* occurrence, and carrying them would produce a chore already dated for a
  day that has gone and assigned to whoever happened to do the last one.
- **R156 The agenda offers it too**, from the popup an item opens into - which
  is reachable from the Day rows, the week and month grids, the undated panel
  and the overdue strip, so one control serves every view.
- **R157 A duplicate goes on the list it came from.** No list picker: the
  agenda crosses lists, and "which list?" is a question the member did not come
  here to answer. It follows that the agenda's form is the **list's** form,
  drawn from that list's declared fields and its members.
- **R158 Saving from the agenda updates both the agenda and the list.** The new
  chore appears where it is due, and the list it was added to is not left one
  item short.

### Explicitly not required

- **Duplicating onto a different list.** That is a move or a copy-to, a
  different feature with a different question to ask. Here, the list is the one
  answer nobody has to give.
- **Duplicating several at once.** The bulk gesture on a list is assignment
  (0005 R46). A member duplicating four chores is doing four separate acts of
  judgement about four dates.
- **Copying the completion, the note, or the points.** Those are the record of
  work that was done. A copy has done nothing.
- **A "duplicate" relationship between the two items.** The copy is an
  ordinary item. Nothing reads a lineage, and storing one would invite a
  feature that does.
- **Duplicating a standing instruction, or a collection entry.** Plausible
  separately; neither is the gesture this is about.
- **Duplicating from an agenda *row*.** The row has two write controls already
  (0010 R83, 0015 R138) and a third icon on a phone is a mis-tap. The popup is
  one tap away and is where a member goes to think about a chore.

## Consequences accepted deliberately

1. **The agenda now creates items**, which widens 0010's "writable in exactly
   one way" a second time. It is bounded by R157: the only create reachable
   from here is one whose list is already decided.
2. **The agenda's dialog fetches the list's configuration** when it opens -
   two requests the rest of the agenda never makes. They are cached under the
   list's own keys, so a member who then opens that list pays nothing.
3. **A duplicate of an item whose list has since dropped a field** quietly
   loses that value. Correct: the form is the list's current form, and the
   server would drop an undeclared value anyway.
4. **Two items with the same description can now exist on one list**, and did
   before - nothing enforces uniqueness, because "Buy milk" twice is a real
   thing a household means.

## Open questions

- **Whether a duplicate should offer the original's date, one period on** -
  "same day next month". Attractive, and a guess: an interval nobody stated is
  what standing instructions are for.
- **Whether the list's row should offer it on a collection entry too.** A
  recipe variant is a plausible case, and the entry's own screen is where it
  would go.
