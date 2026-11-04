# 0004 - Lists, items, and personal settings

Status: accepted
Date: 2026-11-05
Refines: [0001](0001-shared-household-todo.md) R1-R6 (what a list and an item are,
and who may see them)
Amends: [0002](0002-accounts-and-administration.md) (a member now edits their own
password), [0003](0003-pwa-and-ui-shell.md) R15 (navigation) and its exclusion of a
user-chosen colour scheme

## Problem

The product does not exist yet. Accounts, sign-in and an installable shell are all
real, but `/lists` is a placeholder page, there is no list and no item, and a link
shared from Chrome is held on the device because there is nothing to make of it.
Everything shipped so far is scaffolding around an empty middle.

Two premises in the earlier documents also turn out to be wrong, and they are wrong
in ways that block the middle rather than merely annoy.

0001 assumed a **household** entity that lists belong to, and left "how does a
member join a household" as an open question that R6 depended on. That question has
no good answer because the premise is wrong: this is one family's own deployment,
so the deployment *is* the household and there is nothing to join. What a family
actually needs is the opposite of household isolation - a way for one list to be
seen by everybody and another to be seen by nobody. A shared grocery list and a
private workout list are both ordinary, and a model where every member sees every
list cannot express the second.

0002 declared that editing a user after creation was not required, on the reasoning
that delete-and-re-add is good enough. That holds for a display name. It does not
hold for a password: a child who wants a password, or a parent who wants to stop
typing one, should not need an administrator, and losing a password should not mean
losing the account.

## Who uses it

- **Members**, on phones, mostly in a kitchen. A member creates lists, decides who
  sees each one, adds and completes items, and is credited for the work they did.
  They also own their own appearance, their own picture and their own password.
- **The list's creator**, who is just a member with one extra concern: the shape of
  their list and its audience.
- **The admin** (0002 R8), who administers *accounts* and nothing else. They are
  the recovery path for a forgotten password and have no special view of any list.

## Requirements

### Must have

- **R18 A list is a named thing with a face.** A member creates a list with a name,
  an optional description, and an icon chosen from a curated set covering household
  use - chores, shopping, money, health, exercise, study, home, pets, travel. An
  icon is how a list is recognised at a glance on a phone, so it is required and
  chosen from a fixed set rather than typed.
- **R19 Access is granted per list, not by household.** The creator chooses which
  other members can see the list. A member who was not granted a list cannot read
  it, cannot write to it, and cannot discover that it exists - not from a URL, not
  from a search, not from an error message. "Ana's workout" and "Groceries" live in
  the same deployment without being the same audience.
- **R20 The creator manages the list.** The name, description, icon, field
  configuration, access list and deletion belong to the creator. Everyone granted
  access reads and writes the list's items as a peer. The admin administers
  accounts, not list contents, and gains nothing on a list they were not granted.
- **R21 Item fields are declared per list.** A list declares which fields its items
  carry, because a shopping list and a chore roster are not the same shape.
  **Description** is always present and always required. **Detail** (rich text with
  images), **assignee**, **due date and time** and **cost** can each be enabled, and
  each enabled field can be marked required.
- **R22 A list may declare several named cost fields.** A cost field is named by the
  creator and configured either as **fixed choices** - for example 5 min, 30 min,
  60 min - or as a **numeric amount** in a unit the creator names, such as dollar or
  hour. A list may carry more than one: a chore can cost both time and money, and
  those are different questions with different units.
- **R23 Points are optional, and are earned by whoever finishes the work.** With a
  cost field the creator may attach points: a point value per fixed choice, or a
  rate per unit for a numeric cost, so a rate of 1000 per hour makes a one-hour item
  worth 1000. Points are credited to the member who **completed** the item, not to
  its assignee, because the point of points is doing the work rather than being
  given it. Points are totalled per list per member.
- **R24 Assignee is drawn from the list's own audience.** The assignee choices are
  exactly the people with access to that list, so an item can never be assigned to
  someone who cannot see it.
- **R25 Navigation is a sidebar.** The signed-in shell carries a sidebar with three
  sections: the member's **pinned** lists; their **recent** lists together with a
  way to create one; and a **menu** whose single entry today is **List**. On a phone
  the same sidebar is reached from the app bar rather than occupying the screen.
- **R26 All lists, findable.** The List menu entry shows every list the member can
  access, searchable by name and filterable by at least whether they own it or it
  was shared with them, and by whether it is pinned.
- **R27 Pinning is personal.** A member pins the lists they care about. One member's
  pins have no effect on another's.
- **R28 Recent means the lists this member touched.** The recent section shows the
  lists this member actually opened or changed, most recent first, and is per
  member for the same reason pins are.
- **R29 An item carries a value for each field its list declares.** A member adds an
  item to a list, filling the fields that list declared, and a field marked required
  must be filled. Items can be edited and deleted.
- **R30 Any member with access completes any item, and nobody's change vanishes.**
  Completion and un-completion are open to every member of the list, not only the
  assignee. When two members act on the same item at once, the second is told their
  view was stale rather than having their change silently discarded or silently
  overwriting the first.
- **R31 A shared link becomes an item.** A link shared into the app from another
  app becomes an item on a list the member picks, without retyping or pasting.
- **R32 A member has a face.** A member uploads and removes their own profile
  image, which appears wherever they are named - including on the sign-in screen,
  so a child can pick their own picture instead of reading names.
- **R33 A member sets or clears their own password.** They choose whether a password
  is required at all, and set a new one without presenting the old one: an
  authenticated session already proves as much as this product asks for. The admin
  can set or clear any member's password, which is the recovery path when a member
  loses theirs. The admin's own password is deployment configuration (0006) and
  cannot be changed this way.
- **R34 Light, dark, or follow the device.** The colour scheme is a stored per-member
  preference with three values. It follows the member to another device, because it
  is a property of the person and not of the browser. This supersedes 0003's
  exclusion of a user-chosen colour scheme.
- **R35 Material Design.** The interface is built on a Material Design component
  library rather than hand-written styles.

### Explicitly not required

- Groups, roles or permission levels on a list. A grant is binary: you can see the
  list and act on it, or you cannot.
- Transferring a list to another owner, or a list with two owners. A list the owner
  no longer wants is deleted.
- Nested lists, list templates, archiving, recurring items, reminders, due-date
  notifications, or comments on items.
- Spending points on anything, or a household-wide score. Points accumulate per
  list; what they are ultimately for is a later decision and does not need making
  to make them worth earning.
- User-supplied icons, emoji, or per-list colours.
- Password strength rules, lockout, or any proof of the old password. R33 is
  deliberate, not an oversight.
- Receiving shared **files** or images. Still only a link, as in 0003.
- Offline use. Still out of scope from 0001.

## Consequences accepted deliberately

1. **A passwordless member's session can set a password on that account.** Anyone
   who can reach the deployment can already sign in as a passwordless member
   (0002), so R33 adds no new exposure - but it does mean one sibling can set a
   password on another's account and lock them out until the admin clears it. The
   recovery path is why R33 gives the admin a reset. Accepted at household scale,
   and recorded so it is not rediscovered as a bug.
2. **Per-list access is privacy from the family, not security against an
   attacker.** R19 keeps a private list out of a sibling's view. It does not
   withstand anyone who can reach the deployment, because the sign-in roster is
   public and passwordless accounts still exist. A list whose contents would matter
   to a stranger does not belong in this product.
3. **The admin cannot see a list they were not granted.** That is the point of R19,
   and it means there is no administrative route to recover or inspect a list whose
   owner will not share it. Deleting the owner's account deletes their lists.

## Open questions

- Where a completed item should go: stay in place with a line through it, or move
  out of the way into a collapsed section? Unchanged from 0001.
- Whether points already credited can be revoked - by un-completing an item, or by
  the list's owner. Today un-completing takes the points back, which is the simple
  answer but may not be the fair one.
- Whether a shared list should show who else has it open, which is the natural next
  question once two members are editing the same list regularly.
