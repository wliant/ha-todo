# 0018 - Detail HTML is sanitized on write, server-side

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md)
R21 (the detail field)

## Context

R21's detail field is rich text: a member writes steps, notes and formatting, and
embeds images. That means the product stores HTML authored by a user and renders it
back into the same origin that holds the session cookie. Unfiltered, that is stored
cross-site scripting - and the audience is other household members, so one member's
item can run script in another member's session.

`HttpOnly` on the session cookie does not help. A script running in the page does
not need to read the cookie; it can call the API as the member, create and delete
lists, change their password through `PUT /api/me/password`, and read every list
they can see.

Images make it sharper. A field that accepts `<img>` accepts `onerror`, `src` of
`javascript:`, `data:` URLs carrying markup, and off-site URLs that leak a read
receipt to a third party. And an editor that inlines pasted images as base64 would
put megabytes into a `TEXT` column that every item read returns.

## Decision

**All detail HTML is sanitized on the server, on write, before persisting.** The
stored value is the sanitized value; there is no "sanitize on read" step and no
unsanitized copy kept anywhere.

Dependency: `com.googlecode.owasp-java-html-sanitizer`. One component,
`com.hatodo.richtext.HtmlSanitizer`, holding a single static `PolicyFactory` built
once:

- Block and inline formatting, headings, lists, and `<a href>` limited to `http`,
  `https` and `mailto` with `rel="noopener noreferrer"` forced.
- `<img src>` allowed **only** when it matches `^/api/images/[0-9a-f-]{36}$`.
- Nothing else: no `style`, no `class`, no `id`, no `on*` handler, no `<iframe>`,
  no `<svg>`, no `<object>`, no `<form>`.
- A result that is whitespace-only is stored as `NULL`, so "no detail" has one
  representation.

The single `img src` pattern is doing a lot of work on purpose. It eliminates
`javascript:`, `data:` and off-site images in one rule, and it enforces that every
image in an item went through our own uploader - which is what makes the stored
image's access check meaningful. An image a member cannot see is an image whose
bytes `GET /api/images/{id}` will refuse (adr/0013).

**The client never sanitizes.** It renders stored detail with
`dangerouslySetInnerHTML` on API output only. A client-side sanitizer would be a
second implementation of the same policy, and two implementations of one rule
diverge - at which point the one that matters is still the server's, so the client's
only contribution is false confidence.

**The editor uploads rather than inlines.** Paste and drop are intercepted and sent
to the image endpoint, and the editor inserts the returned `/api/images/{id}`
reference. If that interception is ever bypassed, the sanitizer strips the `<img>`
and the member loses the image - a visible, harmless failure rather than a stored
one.

**This ADR is written before the code it governs.** The list's field configuration
only *declares* that items will carry a detail field; no HTML is stored until items
ship. The decision is recorded now so the item work starts from a policy, rather
than shipping an editor whose sanitization is "React escapes it, probably" and
retrofitting one afterwards.

## Consequences

- One place to audit, and it runs on a path that cannot be skipped: every create
  and update goes through the service that calls it.
- A legitimate construct the policy omits is silently dropped, and the member sees
  their formatting vanish with no explanation. Accepted; the fix is widening the
  allowlist deliberately, and an allowlist that fails closed is the right default.
- Sanitizing on write means a policy change does not retroactively clean rows
  written under the old one. If the policy is ever tightened for a security reason,
  the stored HTML must be re-sanitized by a migration - noted here because the
  alternative, sanitizing on read, trades this for a permanent per-request cost and
  an unsanitized value sitting in the database.
- Rendering with `dangerouslySetInnerHTML` is exactly as dangerous as the sanitizer
  is correct, which is why the sanitizer gets unit tests naming specific attacks -
  `<script>`, `onerror`, `javascript:` href, `data:` image, off-site image, `<svg>`
  - alongside a test that ordinary formatting survives.
- The `img` pattern couples the sanitizer to the image URL shape. Changing that
  route means changing this regex, and a mismatch silently strips every image. The
  test suite pins both.

## Alternatives considered

- **Store Markdown instead of HTML.** Genuinely safer - the dangerous surface
  shrinks to link and image URLs - and a reasonable fallback if this policy proves
  painful. Rejected because R21 asks for a rich text area with embedded images, and
  Markdown moves the same image-URL problem one layer out while adding a rendering
  step that can itself be told to emit raw HTML.
- **Sanitize on read.** Lets the stored value stay verbatim, which makes a policy
  change retroactive for free. Rejected: it pays the cost on every read instead of
  every write, and it leaves hostile markup in the database where any future code
  path that forgets to sanitize - an export, a search index, a notification - ships
  it unfiltered.
- **Sanitize in the browser before sending.** Convenient and completely ineffective:
  the API is the boundary, and a hand-written POST skips the browser entirely.
- **A Content Security Policy instead of sanitizing.** Worth having as defence in
  depth, and not a substitute: a CSP does not stop an off-site `<img>` beacon or an
  `<a href="javascript:">`, and the app is served by nginx with no CSP today, so
  adopting one is a separate change with its own compatibility work.
- **Escaping the HTML and rendering it as text.** Safe and not the feature.
