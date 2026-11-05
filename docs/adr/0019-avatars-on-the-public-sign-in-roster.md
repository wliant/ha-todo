# 0019 - Avatars on the public sign-in roster

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R32
Amends: [0009](0009-opaque-sign-in-roster.md) - one field is added to the roster
payload; the prohibition otherwise stands, and the reasoning for it is unchanged.

## Context

adr/0009 cut `GET /api/auth/users` to `id`, `username` and `displayName` and was
emphatic about it: the endpoint is `permitAll`, so anything it carries is published
to anyone who can reach the deployment, and CLAUDE.md records the rule as "Never add
a field to it."

R32 gives members a profile image and asks for it on the sign-in screen. That is not
decoration. The sign-in screen exists because requirements/0002 found that asking a
child to recall a credential was the wrong interaction - and a child who cannot yet
read reliably is in the same position with a list of names. A row with a face is
something a four-year-old can pick; a row with "Ana" is not.

So the question is whether this specific field is the kind of thing adr/0009 was
protecting against.

## Decision

**`avatarImageId` is added to `LoginUserResponse`, and nothing else ever is.**

The test adr/0009 was really applying is not "how many fields" but **what each field
tells an unauthenticated visitor about which account is worth attacking**. That is
why `role` and `passwordRequired` had to go: the first identifies the account that
administers the household, and the second is a list of accounts with no secret at
all - "a list of free passes, sorted by name".

An avatar fails that test in the attacker's favour. It discloses what a member looks
like to someone who is already looking at their name, and says nothing about their
privileges, whether they have a password, or what reaching their account would
yield. It does not reorder the targets.

The disclosure it does add is a photograph of a family member, readable by anyone who
can reach the deployment. That is a real privacy cost and it is accepted knowingly:
it is the same deployment that already publishes the household's names, behind the
same HTTPS front door, and the member chose to upload the picture. A member who does
not want their face published simply does not upload one - initials are the default
and remain a complete sign-in experience.

Mechanically, the roster carries only the **id**. The bytes come from
`GET /api/images/{id}`, which requires authentication - so an anonymous visitor to
the sign-in screen sees the avatars (the endpoint must allow it, since the screen
cannot be drawn otherwise) and this is the one place where `AVATAR` images are
readable without a session. The implementation must therefore permit anonymous reads
of `AVATAR`-purpose images specifically, and must not widen that to `DETAIL` images,
which inherit their list's access rule.

Everything else about adr/0009 is untouched. The client still discovers whether a
password is needed by posting without one and reading the 401, so a deliberate 401
remains part of a successful sign-in. `GET /api/admin/users` still carries `role`
and `passwordRequired` because it is `hasRole("ADMIN")`.

## Consequences

- The sign-in screen becomes usable by a household member who cannot read, which is
  R32's actual point and was R11's before it.
- An anonymous visitor can enumerate the household's faces as well as its names.
  Accepted above; it is the largest single widening of the public surface this
  product has made, and it is recorded here rather than buried in a DTO.
- `AVATAR`-purpose images are anonymously readable and `DETAIL` images are not, so
  the image endpoint's authorization is purpose-dependent rather than uniform. That
  asymmetry is load-bearing and belongs in a test: an anonymous request for a
  `DETAIL` image must 404, and for an `AVATAR` must succeed.
- The rule CLAUDE.md states needs restating rather than deleting: the roster carries
  identity and appearance, and never anything about privileges or credentials. A
  future field has to argue against this ADR's test, not just against a count.
- An avatar id is a stable identifier visible to anonymous callers. It is a random
  UUID that reveals nothing and changes when the image is replaced.

## Alternatives considered

- **Keep the roster at three fields; show avatars only after sign-in.** Leaves
  adr/0009 absolute, which has real value - an emphatic rule is easier to hold than
  a rule with one exception. Rejected because it declines the requirement: the
  screen that needs the faces is the one before sign-in.
- **A separate `permitAll` endpoint serving only avatar ids**, leaving
  `LoginUserResponse` untouched. Satisfies the letter of adr/0009 while publishing
  exactly the same data from a second route, and the sign-in screen would make two
  requests to assemble one list. Rejected as bookkeeping dressed as a decision.
- **Serve avatar bytes inline in the roster as data URLs.** One request, no
  anonymous image endpoint. Rejected: it puts every member's image into every load
  of the sign-in screen, defeats caching, and makes the payload unbounded.
- **Let each member choose whether their avatar is public.** The careful answer, and
  it adds a per-user visibility flag, a UI for it, and a second code path through the
  roster - to solve a problem a family can solve by not uploading a photo. Rejected
  as disproportionate; revisit if this product ever leaves a household.
