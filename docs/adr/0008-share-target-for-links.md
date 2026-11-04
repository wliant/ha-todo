# 0008 - Receiving a shared link: GET share target, parked until lists exist

Status: accepted
Date: 2026-11-04

## Context

R14 wants a member who finds a page in Chrome to share it into "List" and have it
become an item. Two things constrain how.

First, the destination does not exist. Lists and items (0001 R1-R5) are not
implemented, so there is nothing for a shared link to become. The share path is
still worth building now - it is the reason the app is installed - but it cannot
create an item.

Second, `share_target` has two methods. `POST` with `multipart/form-data` is
required for receiving files and must be handled inside a service worker, which
intercepts the POST and re-routes it into the SPA. `GET` arrives as an ordinary
navigation with query parameters and needs no service worker involvement at all.
0003 does not ask for shared files.

Chrome on Android implements the Web Share Target API. Safari on iOS does not,
and an installed iOS web app cannot register itself in the iOS share sheet.

## Decision

Declare a **GET** share target at `/share?title=&text=&url=`. `SharePage` reads
those parameters, normalises them into one captured link, and **parks** it in
`localStorage` keyed by the signed-in user. `ListsPage` shows parked links and
can discard them, labelled as waiting for lists to exist.

Normalisation matters: source apps are inconsistent, and Chrome often delivers
the URL inside `text` rather than `url`. The URL is taken from `url`, else
recovered from `text`, with whatever remains kept as the note.

`/share` stays behind `ProtectedRoute`, so a share arriving while signed out
lands on sign-in and resumes to the share afterwards.

Accept that R14 is **Android-only**, and do not attempt to simulate it on iOS.
Because the route is a plain GET URL, an iPhone user who wants it can build an
iOS Shortcut that opens `/share?url=...`, and pasting a link works everywhere.

## Consequences

- No service worker logic is involved in sharing, which keeps adr/0007's empty
  worker empty.
- Sharing works on Android the day this ships, and nothing is dropped: the link
  is captured and visible.
- The app does something slightly unsatisfying - it holds a link instead of
  filing it. It says so plainly rather than implying an item was created.
- Landing the link on a real list later is one call site in `SharePage`:
  `park(link)` becomes the create-item mutation, and `share/parkedLinks.ts` is
  deleted. The requirement's open question (ask which list, or a default inbox?)
  is answered then, not now.
- Parked links live on one device and in one browser profile, and do not survive
  clearing site data. For a staging area measured in days, that is enough.
- Receiving a shared **file** or image later means switching to a POST target and
  a service worker handler - a different design, and its own ADR.

## Alternatives considered

- **POST share target now.** Needed only for files, which are out of scope, and
  it would put request interception into a service worker we specifically keep
  inert.
- **Park the link on the server** (a per-user inbox table). A schema and an API
  for data whose real home is an item table that is about to be written. The
  migration would be written only to be deleted.
- **Block the share target until lists exist.** The app would be installable but
  not the thing it is installed for, and the share plumbing - manifest entry,
  parameter normalisation, the signed-out resume - would be untested until then.
- **Implement lists and items as part of this change.** Right thing eventually,
  wrong thing to bundle: R6 household scoping has an open question (how a member
  joins a household) that this UI work does not need to answer.
