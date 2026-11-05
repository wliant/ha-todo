import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import type { CalendarFeedWarning } from '../api/calendars'

/**
 * Says when a calendar has stopped refreshing (requirements/0007 R71).
 *
 * <p>This is the line that keeps an empty Thursday from being a lie. Before
 * calendars, an empty day meant "nothing is due"; it now also means "nothing is
 * due and no feed told us otherwise", and without this the feature degrades
 * into quietly hiding events. Do not remove it to tidy the page up.
 */
export function StaleFeedsNotice({ feeds }: { feeds: CalendarFeedWarning[] }) {
  if (feeds.length === 0) {
    return null
  }
  return (
    <Alert severity="warning" role="status">
      <AlertTitle>Some events may be missing</AlertTitle>
      {feeds.map((feed) => (
        <div key={feed.feedId}>
          {feed.memberDisplayName}’s {feed.label} calendar{' '}
          {feed.lastSuccessAt
            ? `last updated ${new Date(feed.lastSuccessAt).toLocaleString([], {
                weekday: 'long',
                hour: '2-digit',
                minute: '2-digit',
              })}`
            : 'has never been read'}
          {feed.lastError ? ` – ${feed.lastError}` : '.'}
        </div>
      ))}
    </Alert>
  )
}
