import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useCurrentUser } from '../auth/useCurrentUser'
import { captureSharedLink, park } from '../share/parkedLinks'

/**
 * Where Android's share sheet lands (R14). The manifest declares a GET share
 * target at this route, so the shared link arrives as ?title=&text=&url=.
 *
 * Lists and items do not exist yet, so the link is parked on the device and
 * said to be waiting, rather than a created item being implied. When R1-R5
 * land, the park() call below becomes the create-item mutation - that is the
 * only change this screen needs (adr/0008).
 */
export function SharePage() {
  const [params] = useSearchParams()
  const { data: user } = useCurrentUser()
  // Derived once from the URL that opened this screen: parsing is pure, and
  // the parameters cannot change without a new navigation.
  const [captured] = useState(() => captureSharedLink(params))
  const parked = useRef(false)

  useEffect(() => {
    // Guarded: StrictMode runs effects twice in development, and parking the
    // same share twice would show the member a duplicate.
    if (parked.current || !captured || !user) return
    parked.current = true
    park(user.id, captured)
  }, [captured, user])

  return (
    <section className="screen">
      <h1>Shared to List</h1>

      {!captured && (
        <>
          <p role="status">Nothing came through with that share.</p>
          <Link to="/lists">Back to lists</Link>
        </>
      )}

      {/* Gated on the member, because that is what parking is keyed by: the
          screen must not say "saved" before it has been. */}
      {captured && user && (
        <>
          <p role="status">Saved. It is waiting on your lists.</p>

          <article className="card">
            <h2 className="card-title">
              {captured.title ?? captured.url ?? captured.note}
            </h2>
            {captured.url && (
              <a
                className="card-url"
                href={captured.url}
                target="_blank"
                rel="noreferrer"
              >
                {captured.url}
              </a>
            )}
            {captured.note && <p className="card-note">{captured.note}</p>}
          </article>

          <p className="muted">
            Lists themselves are still being built, so this link is held here until there
            is a list to put it on.
          </p>

          <Link className="button-link" to="/lists">
            Go to lists
          </Link>
        </>
      )}
    </section>
  )
}
