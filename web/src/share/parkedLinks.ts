/**
 * Links shared into the app that have nowhere to go yet.
 *
 * Lists and items are not implemented (requirements/0001 R1-R5), so a shared
 * link is held on the device and shown as waiting to be filed rather than
 * being silently dropped. When items exist, `SharePage` creates one instead of
 * calling `park()` and this whole module is deleted - see
 * docs/adr/0008-share-target-for-links.md.
 */

export interface ParkedLink {
  id: string
  /** Null when the share carried no URL at all, only text. */
  url: string | null
  title: string | null
  /** Whatever came with the share that was not the URL. */
  note: string | null
  sharedAt: string
}

/** Keyed per user: two members on one tablet must not see each other's. */
function storageKey(userId: string): string {
  return `list.parkedLinks.${userId}`
}

const URL_PATTERN = /https?:\/\/\S+/i

/**
 * Normalises what a share sheet actually sends into one captured link.
 *
 * Source apps are inconsistent and Chrome frequently puts the URL in `text`
 * rather than `url`, sometimes with the page title in front of it, so the URL
 * is recovered from the text when the dedicated field is empty.
 */
export function captureSharedLink(
  params: URLSearchParams,
  now: Date = new Date(),
): ParkedLink | null {
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

  return {
    id: `${now.getTime()}-${Math.random().toString(36).slice(2, 8)}`,
    url,
    title,
    note,
    sharedAt: now.toISOString(),
  }
}

export function parkedLinks(userId: string): ParkedLink[] {
  try {
    const stored = window.localStorage.getItem(storageKey(userId))
    const parsed: unknown = stored ? JSON.parse(stored) : []
    return Array.isArray(parsed) ? (parsed as ParkedLink[]) : []
  } catch {
    // A corrupt or unavailable store must not take the screen down with it.
    return []
  }
}

export function park(userId: string, link: ParkedLink): ParkedLink[] {
  const next = [link, ...parkedLinks(userId)]
  write(userId, next)
  return next
}

export function discard(userId: string, id: string): ParkedLink[] {
  const next = parkedLinks(userId).filter((link) => link.id !== id)
  write(userId, next)
  return next
}

function write(userId: string, links: ParkedLink[]): void {
  try {
    window.localStorage.setItem(storageKey(userId), JSON.stringify(links))
  } catch {
    // Private mode, or a full quota. Losing a parked link is better than
    // failing the screen the member is looking at.
  }
}
