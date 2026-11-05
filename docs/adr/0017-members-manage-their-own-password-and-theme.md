# 0017 - Members manage their own password, with the admin as the reset path

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R33, R34
Amends: [0005](0005-pick-a-user-login-with-optional-passwords.md) - the
password-optional model stands; who may change it does not.
[0006](0006-admin-account-from-environment.md) is untouched: the admin's own
password is still environment configuration.

## Context

requirements/0002 declared that editing a user after creation was not required,
reasoning that delete-and-re-add covers it. For a display name that holds. For a
password it does not: a child who decides they want a password, or a parent tired of
typing one, would need an administrator for a change that is entirely about
themselves, and a member who forgets their password loses the account rather than
the password.

There is also no obvious place to put a current-password check. Half the accounts
in this product have no password at all, so "enter your current password to
continue" is unanswerable for exactly the users R33 exists to serve.

## Decision

**`PUT /api/me/password` sets or clears the caller's own password, and takes no
current password.** The body is `{ password }`, where a string sets a bcrypt hash
and `null` clears it - and clearing it *is* turning the requirement off, because
`password_hash IS NULL` remains the single source of truth for "no password
required" (adr/0005). There is still no companion boolean.

The route takes **no user id**. It acts on `CurrentUserProvider.requireUserId()`,
so there is no parameter to tamper with and no authorization decision beyond being
authenticated.

**The route refuses for `ROLE_ADMIN`**, with a 409 explaining that the admin
password comes from the deployment. `AdminAccountInitializer` reconciles that row
from `ADMIN_PASSWORD` on every startup (adr/0006), so a password set through the
API would be silently reverted on the next deploy - worse than refusing.

**`PUT /api/admin/users/{id}/password` is the reset path**, `hasRole("ADMIN")`, same
body, and it refuses to target the admin row for the same reason. This reverses
requirements/0002's "no editing after creation" for passwords only. Usernames
remain delete-and-re-add.

**The justification for no current password** is that the session is the proof. An
authenticated session already carries the full authority of the account - it can
read every list the member can see and act as them anywhere in the product. A
current-password prompt would protect the password specifically while leaving
everything the password guards already open, which is ceremony rather than
security.

## Consequences

- **A passwordless member's account can be captured.** Anyone who can reach the
  deployment can sign in as a passwordless member - already accepted in
  requirements/0002 - and can now set a password on that account, locking the real
  person out. This is the cost of R33 and is accepted deliberately: the household
  is the trust boundary, and the admin reset is why it is recoverable rather than
  permanent. It is recorded in requirements/0004's accepted consequences so it is
  not rediscovered as a vulnerability.
- The exposure is not new in kind. An attacker who could already act as the member
  could already do everything the account can do; they can now also make it
  obvious, which is arguably an improvement in detectability.
- Nothing outside a family deployment should adopt this. Stated plainly in the
  requirement rather than left for a reader to infer.
- Changing a password does not sign other sessions out. Sessions are in-memory and
  single-instance, there is no session registry, and adding one for this is not
  worth it - but it is a deliberate "no", not an oversight: a captured session
  survives the password change that was meant to end it.
- The theme preference travels the same route shape (`PATCH /api/me/preferences`),
  for the same reason: it is the caller's own property, so there is no id in the
  URL and no authorization question.
- The admin screen gains a reset control. Deleting and re-adding a member is no
  longer the answer to a forgotten password, so the household stops losing
  whatever a re-added account would have lost.

## Alternatives considered

- **Require the current password when one is set**, and skip the prompt when it is
  not. Closes the capture of a password-protected account while keeping R33 usable
  for children, and it is the conventional design. Rejected on the user's explicit
  decision, and the reasoning holds up: the asymmetry is confusing to explain in a
  UI, the session already proves as much, and the accounts most likely to be
  captured are the passwordless ones the exception would still leave open.
- **Admin-only password management.** Safest, and what requirements/0002 implied.
  Rejected because it makes a member's own secret somebody else's errand and fails
  R33 outright.
- **A re-authentication step before the change** - pick yourself and sign in again.
  Proof without a typed current password, so it works for passwordless members.
  Rejected as the same ceremony in more steps: a passwordless member's
  re-authentication is a tap, which proves nothing extra.
- **Email or out-of-band password reset.** There is no `email` column and nothing in
  this product sends mail (requirements/0002).
- **Letting the admin change their own password through the API.** Would be silently
  reverted by the next startup reconciliation. Refusing with an explanation is
  honest; accepting and discarding is not.
