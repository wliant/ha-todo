import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import type { PendingTracker } from '../api/trackers'
import { ReadingDialog } from './ReadingDialog'
import { usePendingTrackers } from './queries'

/**
 * Says which daily trackers have nothing recorded today (requirements/0019
 * R171).
 *
 * <p>One quiet strip beside the agenda's other notices, and the only place a
 * tracker appears on a planning surface. A number is not a chore, so it is
 * deliberately not a row in the day, not in the overdue strip and not in any
 * count - the agenda answers "what is left to do", and recording a weight is
 * not something the household owes anybody.
 *
 * <p>Daily trackers only, and only today's bucket: a monthly tracker asked
 * about every morning is a reproach for four weeks, which is the opposite of a
 * reminder. The <em>server</em> decides both, and names the bucket it is
 * asking about, so the Add control never recomputes "today" from the device.
 *
 * <p>Renders nothing at all when there is nothing missing - including while
 * the request is in flight and when it failed. That is the one place this
 * differs from the sidebar's three-state rule, and deliberately: a nudge is
 * not somebody's data, and "could not check whether you weighed yourself" is
 * noise on the landing page.
 */
export function PendingTrackersNotice() {
  const { data: pending } = usePendingTrackers()
  const [recording, setRecording] = useState<PendingTracker | null>(null)

  if (!pending || pending.length === 0) {
    return null
  }

  return (
    <>
      <Alert
        severity="info"
        role="status"
        action={
          // One control when there is one tracker, which is the usual case.
          // With several, each gets its own button below rather than a chooser
          // here: picking which number to record is the whole decision.
          pending.length === 1 ? (
            <Button size="small" onClick={() => setRecording(pending[0])}>
              Record
            </Button>
          ) : undefined
        }
      >
        {pending.length === 1 ? (
          <>Nothing recorded today for {pending[0].name}.</>
        ) : (
          <Stack spacing={0.5} sx={{ alignItems: 'flex-start' }}>
            <span>Nothing recorded today for:</span>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
              {pending.map((tracker) => (
                <Button key={tracker.id} size="small" onClick={() => setRecording(tracker)}>
                  {tracker.name}
                </Button>
              ))}
            </Stack>
          </Stack>
        )}
      </Alert>

      {recording && (
        <ReadingDialog
          key={recording.id}
          trackerId={recording.id}
          period="DAY"
          unit={recording.unit}
          // The bucket the server named, not one computed here (R166).
          defaultPeriodStart={recording.periodStart}
          onClose={() => setRecording(null)}
        />
      )}
    </>
  )
}
