import { useState } from 'react'
import { useCurrentUser } from '../auth/useCurrentUser'
import { discard, parkedLinks, type ParkedLink } from '../share/parkedLinks'

/**
 * Placeholder. The household / list / item domain lands on its own feature
 * branch; what is real here is the shared links waiting for it (adr/0008).
 */
export function ListsPage() {
  const { data: user } = useCurrentUser()
  const [waiting, setWaiting] = useState<ParkedLink[]>(() =>
    user ? parkedLinks(user.id) : [],
  )

  return (
    <section className="screen">
      <h1>Lists</h1>

      <p role="status">No lists yet.</p>

      {waiting.length > 0 && (
        <section className="stack">
          <h2>Shared links waiting</h2>
          <p className="muted">
            Shared into List before there was a list to put them on. They will become
            items once lists exist.
          </p>

          <ul className="card-list">
            {waiting.map((link) => (
              <li key={link.id} className="card">
                <div className="card-body">
                  <span className="card-title">
                    {link.title ?? link.url ?? link.note}
                  </span>
                  {link.url && (
                    <a
                      className="card-url"
                      href={link.url}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {link.url}
                    </a>
                  )}
                </div>
                {/* Labelled rather than captioned: on a phone the link needs
                    the width more than the control does. */}
                <button
                  type="button"
                  className="ghost card-discard"
                  aria-label={`Discard ${link.title ?? link.url ?? 'link'}`}
                  onClick={() => setWaiting(discard(user!.id, link.id))}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  )
}
