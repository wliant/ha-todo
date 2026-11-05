import { useEffect, useRef } from 'react'
import Button from '@mui/material/Button'
import ButtonGroup from '@mui/material/ButtonGroup'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import CheckRounded from '@mui/icons-material/CheckRounded'
import NoteAltRounded from '@mui/icons-material/NoteAltRounded'

/** How long a press has to last before it means "and let me write a note". */
const HOLD_MS = 1000
/** A press that wanders this far was the start of a scroll, not a hold. */
const SLOP_PX = 10

/**
 * Completing an item (R48).
 *
 * <p>A press completes it. Holding for a second offers the completion note
 * instead - but the hold is only an accelerator, never the way in. A hold is
 * not something a keyboard can do (holding Enter on a button fires repeated
 * clicks, not one long press), so a hold-only route to the note would fail
 * WCAG 2.1 SC 2.5.1 with no alternative, and a hint saying "hold for a note"
 * tells a keyboard user about something they still cannot reach.
 *
 * <p>So the note has its own visible, tabbable button, and that button *is* the
 * single-pointer alternative. See adr/0027.
 */
export function CompleteControl({
  description,
  onComplete,
  onWriteNote,
}: {
  description: string
  onComplete: () => void
  onWriteNote: () => void
}) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const origin = useRef<{ x: number; y: number } | null>(null)
  // Set when the hold has already opened the dialog, so the click that follows
  // releasing the pointer does not also complete the item.
  const handled = useRef(false)

  useEffect(() => cancel, [])

  function cancel() {
    if (timer.current !== null) {
      clearTimeout(timer.current)
      timer.current = null
    }
    origin.current = null
  }

  function onPointerDown(event: React.PointerEvent) {
    handled.current = false
    origin.current = { x: event.clientX, y: event.clientY }
    timer.current = setTimeout(() => {
      timer.current = null
      handled.current = true
      // A short buzz, where the device does them: the gesture is invisible
      // otherwise, and a member cannot tell a hold took from one that did not.
      navigator.vibrate?.(10)
      onWriteNote()
    }, HOLD_MS)
  }

  function onPointerMove(event: React.PointerEvent) {
    const from = origin.current
    if (!from) {
      return
    }
    if (Math.abs(event.clientX - from.x) > SLOP_PX || Math.abs(event.clientY - from.y) > SLOP_PX) {
      cancel()
    }
  }

  return (
    <ButtonGroup variant="outlined" size="small">
      <Button
        startIcon={<CheckRounded fontSize="small" />}
        aria-label={`Complete ${description}`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={cancel}
        onPointerLeave={cancel}
        onPointerCancel={cancel}
        // On iOS a long press on a button raises the selection callout, which
        // would land on top of the dialog the hold just opened.
        onContextMenu={(event) => event.preventDefault()}
        onClick={() => {
          if (handled.current) {
            handled.current = false
            return
          }
          onComplete()
        }}
        sx={{ touchAction: 'manipulation', userSelect: 'none', WebkitTouchCallout: 'none' }}
      >
        Complete
      </Button>
      <Tooltip title="Complete and say how it went">
        <IconButton
          size="small"
          aria-label={`Complete ${description} with a note`}
          onClick={onWriteNote}
          sx={{ borderRadius: 0 }}
        >
          <NoteAltRounded fontSize="small" />
        </IconButton>
      </Tooltip>
    </ButtonGroup>
  )
}
