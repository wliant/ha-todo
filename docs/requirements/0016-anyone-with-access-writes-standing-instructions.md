# 0016 - Anyone with access writes standing instructions

Status: accepted
Date: 2026-11-15
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) - whose
R41 gave a list standing instructions and left writing them to the owner, on
[0004](0004-lists-items-and-personal-settings.md) R20's reading that the shape
of a list is the owner's business.

## Problem

A shared list is shared work, and a recurring chore is the most shared thing on
it. Bins out on Tuesday, the water filter on the first of the month, the plants
on Wednesdays - these are not somebody's personal arrangements, they are what
the household has agreed to keep doing. Today only one person in the household
can write any of them down.

The consequence is not that the rules go unwritten. It is that they are written
*by hand, every week*, by whoever noticed. A member who knows the filter needs
doing monthly has two choices: add the item manually each month and keep
remembering, or ask the list's owner to add a rule and wait. Both of those are
the product failing at the one thing a standing instruction exists for, which
is to stop somebody having to remember.

The original reasoning was that a rule is as much the shape of a list as its
fields are, and the shape is the owner's (0004 R20). That reading is wrong, and
it is wrong in a specific way: a field changes what *every* item on the list
must answer, including items other people add - it constrains everybody. A rule
adds items. An item is exactly what every member with access can already add,
one at a time, with no ceremony at all. A rule is a member saying "and again
next Tuesday" about a thing they were already entitled to say once.

So the product currently allows the larger act and refuses the smaller one,
which is the shape of the problem.

## Who uses it

- **The member who keeps a chore going.** They know the filter is monthly
  because they are the one who changes it. They should be able to write that
  down without asking anybody.
- **The member who shares a list they do not own.** Most lists in a household
  have one owner and several members, and the members are not guests.
- **The list's owner.** They stop being a bottleneck for other people's
  routines, and keep the authority to tidy up anything on their list.

## Requirements

### Must have

- **R141 Any member with access to a list can add a standing instruction to
  it.** The same grant that lets them add an item, with no further permission.
  Creating one is a write at the *readable* level, not the managed level.
- **R142 A standing instruction records who wrote it, and says so on screen.**
  It already records it; it has never shown it. With several members writing
  rules on one list, "who decided this" is a real question, and a list of
  anonymous instructions reads as though the list made them itself.
- **R143 A standing instruction is edited and deleted by whoever wrote it, or
  by the list's owner.** Not by any member with access. Rewriting somebody
  else's standing arrangement is a different act from writing your own, and the
  owner keeps final authority over everything on their list - including the
  rules of a member whose access has since been revoked.
- **R144 A member sees only the controls they can use.** The edit control
  appears on the rules they may write, and on no others. The server refuses the
  rest regardless, but a button that always fails is a bug report.
- **R145 Every existing constraint on a rule still applies, unchanged.** An
  assignee must be a member with access (0005 R24); the costs a rule answers
  must be the list's; the fields a rule may fill are the ones the list declares.
  Who wrote the rule changes none of this.
- **R146 Running the rules by hand stays open to any member with access.**
  Unchanged from 0005. What a run produces is an item they could have typed.
- **R147 The list's configuration stays the owner's.** Fields, costs, the
  grants, the list's name and its deletion are all unchanged. This requirement
  moves exactly one thing.

### Explicitly not required

- **Any member editing any rule.** R143 settles it. Items are symmetric -
  anybody with access may complete anybody's item (0004 R30) - because
  completing is reporting a fact about the world. Editing a standing
  instruction is changing what the list will do next month on somebody else's
  behalf, which is not the same kind of act.
- **Transferring a rule to another member.** A rule's author does not change.
  If the wrong person wrote it, it is deleted and written again.
- **Telling the owner that somebody added a rule.** There are no notifications
  anywhere in the product, and this is not the feature that introduces them.
  The rule appears on the tab with its author's name on it.
- **A per-list setting for whether members may write rules.** A switch nobody
  will find, defending against a problem a household of four does not have. If
  it turns out to be needed it is a later, deliberate change.
- **Reordering another member's rules.** Position is not editable through the
  UI at all today; nothing here changes that.

## Consequences accepted deliberately

1. **A member can create work for the whole household on a recurring basis.**
   That is the feature. The blast radius is bounded by the list's grants - the
   same bound that already lets them add items, assign them within the list,
   and complete anybody's - and every rule carries its author's name, so an
   instruction nobody wants has somebody to ask about it.
2. **Two kinds of permission now exist on one tab.** Adding is open to
   everybody with access; editing depends on the row. That is more to hold in
   mind than "the owner writes these", and R142 and R144 are what make it
   legible rather than surprising: the name on the rule is the reason the edit
   control is or is not there.
3. **A rule outlives its author's access.** Revoking a grant already unassigns
   that member from every rule on the list (0005 R24) but does not delete the
   rules they wrote, and should not - the household may well still want the
   bins out. The owner can edit or remove them, which is the other half of why
   R143 keeps the owner in.
4. **The guarantee that an ungranted list is undiscoverable is unchanged.** A
   rule on a list the caller cannot see is still absent rather than refused.

## Open questions

- **Whether a rule should show how its occurrences have been going**, which
  0015 raised: something lapsed every week is a rule that is wrong. More
  interesting now that the member who wrote it may not be the owner.
- **Whether the owner should be able to see, in one place, every rule any
  member has added across their lists.** Plausible if a household turns out to
  write many; not a problem four people have.
