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
