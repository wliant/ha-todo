/**
 * What a share sheet actually sent, normalised.
 *
 * The app is a share target on Android (adr/0008): a link arrives as
 * `?title=&text=&url=`. Until items existed the link was parked on the device;
 * now it becomes an item on a list the member picks (adr/0020), so what
 * survives from that work is this - the normalisation, which is the fiddly
 * part.
 */
export interface SharedLink {
  /** Null when the share carried no URL at all, only text. */
  url: string | null
  title: string | null
  /** Whatever came with the share that was not the URL. */
  note: string | null
}

const URL_PATTERN = /https?:\/\/\S+/i

/**
 * Source apps are inconsistent, and Chrome frequently puts the URL in `text`
 * rather than `url` - sometimes with the page title in front of it - so the
 * URL is recovered from the text when the dedicated field is empty.
 */
export function captureSharedLink(params: URLSearchParams): SharedLink | null {
  const title = params.get('title')?.trim() || null
  const text = params.get('text')?.trim() || null
  const sharedUrl = params.get('url')?.trim() || null

  let url = sharedUrl
  let note = text

  if (!url && text) {
    const match = text.match(URL_PATTERN)
    if (match) {
      url = match[0]
      note = text.replace(match[0], '').trim() || null
    }
  }

  if (!url && !note && !title) {
    return null
  }

  return { url, title, note }
}

/** What the item should say, given a share can arrive with any subset of parts. */
export function describeSharedLink(link: SharedLink): string {
  const parts = [link.title, link.url].filter(Boolean)
  return parts.length > 0 ? parts.join(' - ') : (link.note ?? '')
}

/**
 * The same share, as a collection entry (R59).
 *
 * The title becomes the name - a member names it on the way in rather than
 * filing something called "https://..." - and the link goes into the detail as
 * an anchor, which the sanitizer keeps (http/https/mailto, rel=nofollow
 * forced). Falls back to the URL as the name when the share carried no title,
 * because an entry must have one.
 */
export function sharedLinkAsEntry(link: SharedLink): {
  name: string
  description?: string
  detailHtml?: string
} {
  const name = link.title ?? link.note ?? link.url ?? 'Shared link'
  const description = link.title && link.note && link.note !== link.title ? link.note : undefined
  const detailHtml = link.url
    ? `<p><a href="${escapeHtml(link.url)}">${escapeHtml(link.url)}</a></p>`
    : undefined
  return { name: name.slice(0, 200), description: description?.slice(0, 500), detailHtml }
}

/**
 * The URL came from a share sheet, so it is not ours. Escaped before it is
 * interpolated into HTML - the server sanitizes on write too, but building
 * broken markup here and relying on that to clean it up is the wrong order.
 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
