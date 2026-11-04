# 0002 - Accounts and administration

Status: accepted
Date: 2026-11-04
Refines: [0001](0001-shared-household-todo.md) R7 (Sign-in)

## Problem

Requirement 0001 R7 says only "a member signs in before seeing anything", and the
skeleton implemented that as an email-and-password form. That is wrong for the
actual users. The household includes people who do not have an email address,
do not want to type one, and in some cases cannot reliably type a password at
all - a child who is expected to tick off their chores will not do it if signing
in is a chore. There is also no way to create an account: the only members who
exist are the two rows a seed migration inserts.

## Who uses it

- **Members** - the family. They sign in by recognising their own name, not by
  recalling a credential.
- **The admin** - one person who owns the household's accounts. The admin is also
  a member of the household in the ordinary sense and signs in the same way,
  except that the admin always needs a password.

## Requirements

### Must have

- **R8 One admin.** Exactly one admin account exists. It always requires a
  password. Its credentials come from the environment, not from the database as
  shipped, and not from the repository.
- **R9 User administration.** The admin can **add** a user (login name, display
  name) and **delete** a user. No other role can.
- **R10 Optional password.** When adding a user, the admin chooses whether that
  user needs a password. A user with no password required signs in by being
  picked - no secret is asked for and none is stored.
- **R11 Pick-a-user sign-in.** The sign-in screen shows the household's users as
  a list to choose from, not a pair of empty text fields. Choosing a user whose
  password is not required signs them straight in. Choosing a user whose
  password *is* required reveals a password field for that user alone. The admin
  appears in the list like anybody else.
- **R12 The admin is undeletable.** The admin account cannot be deleted through
  the API, including by itself, so the household cannot lock itself out of
  administration.

### Explicitly not required

- Self-service sign-up, invite links, or password reset. The admin adds people.
- Editing a user after creation - renaming them, or turning a password
  requirement on or off. Delete and re-add is an acceptable answer at household
  scale; a dedicated edit flow can come later if it is actually missed.
- More than one admin, or any role between admin and member.
- Email addresses. Nothing in this product sends mail, so storing an address
  would be collecting a credential-shaped identifier for no purpose.
- Lockout, rate limiting, or password strength rules.

## Consequences accepted deliberately

This design trades authentication strength for household usability, with the
owner's explicit agreement:

1. **The roster is public.** The sign-in screen has to be readable before anyone
   is signed in, so anyone who can reach the app can see the household's display
   names and who needs a password.
2. **A passwordless user is anyone who can reach the app.** There is no secret,
   so "signing in as Ben" is available to any visitor. Reaching the app is the
   only control.

Both are acceptable because the app is a family's own deployment and the data is
a chore list. They are **not** acceptable for anything else, which is why the
admin account - the only account that can change who exists - keeps a password.
Recorded in [adr/0005](../adr/0005-pick-a-user-login-with-optional-passwords.md).

## Open questions

Unchanged from 0001: how a member joins a *household* (as opposed to being added
as a user) is still open, and still blocks R6. Until the household domain lands,
every user in the database is implicitly in the one household.
