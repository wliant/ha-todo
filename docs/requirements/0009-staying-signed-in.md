# 0009 - Staying signed in

Status: accepted
Date: 2026-11-06
Refines: [0002](0002-accounts-and-administration.md) - its sign-in screen and
its account model, neither of which said how long being signed in lasts.

## Problem

Nobody ever decided how long a session should last, so it was whatever the
framework did: **thirty minutes of inactivity**. On a phone that is close to
the worst possible answer. A member opens the app in the morning, again at
lunch, again before the shops - and picks their name out of a list every single
time, because half an hour is shorter than the gap between the ways a household
actually uses this.

**The friction buys almost nothing here.** The sign-in screen is a list of
everybody in the household and half the accounts have no password at all
(0002). Anybody holding an unlocked phone could sign in as its owner in two
taps. Asking them to do it four times a day protects against nothing; it only
teaches them that the app is tedious to open.

**And the installed app makes it worse, not better.** 0003's PWA sits on the
home screen looking like something that is simply there. Tapping it and landing
on a sign-in screen is the one thing that reveals it to be a web page in a
costume.

## Who uses it

- **Every member, several times a day**, mostly for seconds at a time: check
  what is due, tick one thing off, look at tomorrow. The sign-in is pure
  overhead on every one of those visits.
- **A member who comes back after a holiday**, two weeks later. They should
  find the app as they left it rather than be treated as a stranger.
- **The household, collectively**, if a phone is lost - which is the case where
  a long session costs something rather than saves something.

## Requirements

### Must have

- **R79 A sign-in lasts until it has gone unused for a long time.** Thirty
  days, not thirty minutes. A member who opens the app at all within that window
  is not asked who they are.
- **R80 Using the app renews it.** The clock runs from the **last visit**, not
  from the sign-in. A member who opens the app every day is never signed out by
  the passage of time, however long they keep it up. A rule that expired a daily
  user on day thirty would be a worse version of the problem this replaces.
- **R81 Closing the app does not end the session.** Quitting the browser,
  swapping apps, or restarting the phone leaves a member signed in. Anything
  that only survives while the app is open is, on a phone, barely different from
  signing in every time.
- **R82 Signing out ends it immediately, everywhere it can.** The one control a
  member has over a long-lived session has to work at once and completely - a
  session that lingers after somebody deliberately ended it is the failure that
  makes the whole trade indefensible.

### Explicitly not required

- Staying signed in **across a restart of the application**. Sessions are held
  in memory, so a deployment signs everybody out. That is a known gap, not a
  decision this document makes; it is recorded as a consequence below so nobody
  mistakes it for a bug in the above.
- Remember-me tokens, or any second credential alongside the session. 0002 keeps
  authentication to one obvious path; a parallel mechanism to save a cookie
  attribute is how authorization bugs get in.
- Re-authentication for sensitive actions, biometric or otherwise. There is
  nothing in this product that a household member holding the phone should be
  stopped from doing.
- A member seeing or ending their other devices' sessions. Worth having one day;
  it needs sessions that outlive a restart first.
- Any change to what a session grants. A longer one is the same session.

## Consequences accepted deliberately

1. **A signed-in phone is a thirty-day credential.** The exposure that actually
   changes is a *lost* phone, where the session outlives the owner's awareness
   of the loss. It is a smaller step than it sounds - the sign-in screen names
   every member and half have no password - and the mitigations are the ones
   that already exist: sign out (R82), or have the admin delete the account.
2. **Every deploy still signs everybody out**, because sessions live in the
   application's memory. After this change that becomes the *most likely* reason
   a member is asked to pick their name, which is a strange thing for them to
   experience: the app logs them out at apparently random intervals that
   correspond to nothing they did. Fixing it means persisting sessions, which is
   a dependency and a table and belongs in its own document.
3. **The session cookie is now written to disk**, because a cookie that survives
   closing the app has to be. It is still unreadable to script and still only
   sent over HTTPS in a deployment.
4. **Thirty days is a guess.** It is long enough that a member on holiday comes
   back to a working app and short enough that an abandoned device eventually
   stops working. Nobody will be able to tell whether it is right, which is why
   it is configuration rather than a constant.

## Open questions

- Whether a member should be able to end their sessions on other devices - the
  thing somebody actually wants when a phone goes missing. It needs sessions
  that survive a restart before it can mean anything.
- Whether the admin should be able to sign a member out. Same prerequisite, and
  a different question about what an admin is for (0002 says accounts, not
  contents).
- Whether earned points can be revoked by a list's owner. Unchanged from 0005
  onwards.
- Whether a shared list should show who else has it open. Unchanged from 0004
  onwards.
