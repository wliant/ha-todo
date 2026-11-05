import { Suspense, lazy, useState } from 'react'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Skeleton from '@mui/material/Skeleton'

// ProseMirror is around 130KB gzipped and most completions will not want it.
const DetailEditor = lazy(() =>
  import('./DetailEditor').then((module) => ({ default: module.DetailEditor })),
)

/**
 * Saying how the work went, on the way to completing it (R37).
 *
 * <p>Mounted keyed on the item, and unmounted when closed, because
 * DetailEditor takes `value` as its initial content and never re-syncs it -
 * reusing one instance across two items would show the first item's note on
 * the second.
 */
export function CompletionNoteDialog({
  description,
  onCancel,
  onComplete,
}: {
  description: string
  onCancel: () => void
  onComplete: (noteHtml: string | null) => void
}) {
  const [note, setNote] = useState('')

  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onCancel}>
      <DialogTitle>How did it go?</DialogTitle>
      <DialogContent>
        <Suspense fallback={<Skeleton variant="rounded" height={180} />}>
          <DetailEditor
            value=""
            required={false}
            onChange={setNote}
            label={`How ${description} went`}
            helperText="Optional. A line about what happened, or a photograph of it done."
          />
        </Suspense>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={() => onComplete(note.trim() ? note : null)}>
          Complete
        </Button>
      </DialogActions>
    </Dialog>
  )
}
