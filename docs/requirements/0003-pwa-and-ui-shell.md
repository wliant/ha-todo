# 0003 - Progressive web app and UI shell

Status: accepted
Date: 2026-11-04
Refines: [0001](0001-shared-household-todo.md) R1-R5 (how they are presented),
[0002](0002-accounts-and-administration.md) R9/R11 (administration and sign-in screens)

## Problem

The skeleton's screens are a single centred column rendered by a browser tab.
That is wrong for how this product is actually reached. A household todo list is
used standing in the kitchen with a phone in one hand, not sitting at a desk, and
the people using it do not think of it as a website: they expect an icon on the
home screen that opens without browser chrome. They also expect it to be a
destination for things they find elsewhere - seeing a recipe or a product in
Chrome and wanting it on the shopping list means sharing the link into the app,
not retyping a URL.

Two existing screens are also wrong in their own right. Administration is a form
stacked on top of a flat list, which stops reading as one thing the moment the
household has more than a handful of people. And the sign-in screen volunteers
who is the admin and who needs a password, on a roster that is readable by anyone
who can reach the deployment - it tells an unauthenticated visitor exactly which
name is worth attacking and which is a free pass.

## Who uses it

- **Members** on phones, mostly Android and iPhone, with the app installed to the
  home screen. A member is the one who shares a link in from another app.
- **The admin** doing account work, usually on a larger screen but not always.

## Requirements

### Must have

- **R13 Installable app.** The app is installable to the home screen on Android
  and iPhone, is titled **"List"**, and opens standalone - no browser address
  bar. It has its own icon, including a maskable icon so Android does not frame
  it in a white box, and a themed status bar rather than a stark white strip.
- **R14 Receive a shared link.** On a platform whose share sheet supports it, the
  installed app appears as a share target: a member sharing a link from another
  app lands in "List" with the shared URL and title already captured, ready to
  become an item. Sharing never asks the member to retype or paste anything.
- **R15 Responsive layout with navigation.** Every signed-in screen is reached
  through a persistent navigation bar carrying the member's identity, the lists,
  administration when the signed-in user is the admin, and signing out. The
  layout adapts to the viewport - a phone-width screen is a usable first-class
  layout, not a desktop layout squeezed - and nothing requires horizontal
  scrolling or pinch-zoom.
- **R16 Master-detail administration.** The admin screen is a master-detail
  screen: the household's people on one side, the selected person's detail and
  the actions on them on the other. On a phone the detail replaces the list and
  can be backed out of; on a wide screen both are visible at once. Adding a
  person is a detail pane, not a form permanently occupying the page.
- **R17 The sign-in roster says nothing but who exists.** Each entry on the
  sign-in screen shows that person's name and nothing else. It must not disclose
  their role, nor whether a password is required. The public API behind it must
  not carry those fields either, because the screen is readable by anyone who can
  reach the deployment.

### Explicitly not required

- Offline use. Still out of scope from 0001; the app may be installed, but it is
  not expected to work without the network.
- Push notifications, badging, or any background sync.
- Receiving shared **files** or images. Only a link, with its title and any text
  that came with it.
- A native app, or a store listing, on either platform.
- Theming or a user-chosen colour scheme beyond honouring the device's light or
  dark preference.

## Platform reality accepted deliberately

**R14 can only be delivered on Android.** Receiving a share requires the Web
Share Target API, which Chrome on Android implements and Safari on iOS does not -
an installed iOS web app cannot place itself in the iOS share sheet. So:

- On Android, sharing a link from any app offers "List" directly, and R14 is met
  as written.
- On iPhone, the app is installable and standalone (R13 is met in full), but it
  cannot appear in the share sheet. A member there reaches the same screen by
  pasting a link, and the app accepts a link arriving by URL so a user-created
  iOS Shortcut can feed it one.

This is a platform limit, not a design choice, and it is recorded rather than
worked around. Recorded in
[adr/0008](../adr/0008-share-target-for-links.md).

## Dependency on the unimplemented domain

A shared link's destination is an **item on a list**, and lists and items
(0001 R1-R5) are not implemented yet. Until they are, a shared link is **parked**:
the app captures it, holds it on the device, and shows it as waiting to be filed.
The share path is built end to end so that landing it on a list is a change to
one call site rather than a new feature. No shared link is silently dropped, and
nothing pretends an item was created.

## Open questions

- Where a shared link should land once lists exist: always ask which list, or
  default to a designated "Inbox" list?
- Whether a parked link should survive reinstalling the app. It is on the device
  today, so it does not.
