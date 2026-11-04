# 0003 - Progressive web app and UI shell

Status: accepted
Date: 2026-11-04
Implements: [requirements/0003](../requirements/0003-pwa-and-ui-shell.md) R13-R17
Builds on: [design/0002](0002-accounts-and-administration.md)

## Scope

The installable app wrapper, the responsive signed-in shell and its navigation,
the master-detail administration screen, the share-receiving route, and the
removal of role and password hints from the sign-in roster.

Still out of scope: lists and items themselves. A shared link is parked on the
device (see [Share capture](#share-capture)).

Decisions behind this design:
[adr/0007](../adr/0007-installable-pwa-without-offline-caching.md) (manifest and
a deliberately non-caching service worker),
[adr/0008](../adr/0008-share-target-for-links.md) (GET share target, parked
links, Android only),
[adr/0009](../adr/0009-opaque-sign-in-roster.md) (the roster stops describing
people).

## Backend

One change only, and it is a deletion. `LoginUserResponse` drops `role` and
`passwordRequired`:

```
GET /api/auth/users  ->  [ { id, username, displayName } ]
```

`username` stays because `POST /api/auth/login` is keyed by it; `id` stays as the
list key. Nothing else about authentication changes - `password_hash IS NULL`
remains the single source of truth, and `LoginService.authenticate` keeps its one
explicit branch (adr/0005).

The consequence is that the client can no longer know in advance whether a
password is needed, so it finds out the only way left: it posts the login with no
password, and treats **401** as "this person needs one". A password-required
account already fails that post - `DaoAuthenticationProvider` rejects a null
password - so no backend branch is added for it, and the response stays as opaque
as it was (`AuthController` never says which half was wrong).

`GET /api/admin/users` is unchanged and keeps `role`, `passwordRequired`,
`enabled` and `createdAt`. That endpoint is `hasRole("ADMIN")`, and the
master-detail pane is exactly where those fields belong.

## Frontend

### Shape

```
src/
  app/
    AppLayout.tsx        signed-in shell: nav + <Outlet/>
    NavBar.tsx           identity, Lists, Users (admin only), Sign out
  pages/
    LoginPage.tsx        pick a name -> silent attempt -> password if refused
    ListsPage.tsx        placeholder + parked shared links
    AdminPage.tsx        master-detail: people | selected person or "add"
    SharePage.tsx        /share - captures an incoming link, parks it
  share/
    parkedLinks.ts       localStorage-backed store for captured links
  index.css              design tokens + layout primitives
```

`AppLayout` is a layout route wrapping every authenticated route, so the
navigation bar is mounted once and does not remount per screen. `ProtectedRoute`
and `AdminRoute` keep their current jobs and now guard the layout route and the
`/admin` child respectively.

### Responsive rules (R15)

One breakpoint, at `48rem`. Below it the navigation is a bottom bar - thumb
reach, and it stays clear of the keyboard because it is not fixed over an input.
At or above it the navigation is a top bar with the same items. The breakpoint is
a CSS media query, not a JS viewport hook: there is no behavioural difference
between the two layouts, only placement, so nothing needs to re-render on resize.

`index.css` carries the tokens (colour, spacing, radius, elevation) and honours
`prefers-color-scheme`; components use the tokens and own no colours. Safe-area
insets are respected so the bottom bar is not under an iPhone's home indicator.

### Master-detail administration (R16)

`AdminPage` holds one piece of view state: `selection`, which is a user id, the
literal `'new'`, or nothing. Master is the list of people; detail renders the
selected person (their login name, whether a password is required, when they were
added, and **Remove**) or the add-someone form.

Below the breakpoint, master and detail are two views of one route and the detail
carries a back affordance; at or above it they are two columns and selecting a
person does not hide the list. That is again one media query - the same component
tree renders both.

Deleting the selected person clears the selection, so the detail pane cannot
outlive its subject. The admin row renders in the detail pane like anybody else
but offers no Remove button (R12).

### Sign-in (R17)

The roster rows show `displayName` and nothing else. Choosing a person:

1. posts the login with no password;
2. **200** - signed in, which is the whole flow for a passwordless member;
3. **401** - reveal a password field for that person alone, and post again with
   what they type.

A failed second attempt says only that the sign-in did not work. The screen is
titled **List**.

### Share capture

`manifest.webmanifest` declares a GET share target at `/share`, so Android's
share sheet hands the app `?title=&text=&url=`. `SharePage` is behind
`ProtectedRoute` - a share while signed out lands on sign-in and resumes
afterwards, because the route is preserved rather than replaced.

It normalises the three parameters into one captured link (Chrome puts the URL in
`text` for some source apps, so the URL is taken from `url` or recovered from
`text`), parks it via `parkedLinks.ts`, and says so. `ListsPage` shows parked
links with a way to discard one. The park is `localStorage`, keyed per signed-in
user id, and is explicitly labelled as waiting for lists to exist - no item is
claimed to have been created. When R1-R5 land, `SharePage` calls the create-item
mutation instead of `park()`, and the store is deleted.

## Installability (R13)

`public/manifest.webmanifest` (name "List", `display: standalone`,
`start_url: /lists`, `scope: /`, theme and background colour, 192/512 PNG icons
plus a maskable one), linked from `index.html` along with the iOS-only
`apple-touch-icon` and status-bar meta tags that Safari uses instead of the
manifest.

Icons are generated into `public/icons/` by `web/scripts/generate-icons.mjs`,
which draws them with Node's built-in `zlib` and no dependency, so the committed
PNGs are reproducible from the committed source. `public/sw.js` is registered for
installability only and caches nothing - see adr/0007. nginx serves `sw.js` and
the manifest with `no-store`, for the same reason `index.html` is uncached.

## Testing

- **Unit (`web/`)**: the login probe flow (one tap for a passwordless member; a
  password field only after a refusal; no role or password text anywhere on the
  roster), master-detail selection behaviour including selection clearing on
  delete, share-parameter normalisation including the URL-in-`text` case, and
  that the navigation offers Users to an admin and not to a member.
- **Unit (`app/`)**: `LoginUserResponse` carries no role or password fields.
- **Integration (`app/`)**: the anonymous roster payload has exactly `id`,
  `username`, `displayName`; a password-required account is refused when posted
  with no password; a passwordless one succeeds.
- **E2E (`e2e/`)**: sign-in by picking a name with the roster showing no hints,
  administration through the master-detail screen, and the share route landing on
  a parked link. The manifest and service worker are fetched and asserted to be
  served - installability itself is not assertable from Playwright.
