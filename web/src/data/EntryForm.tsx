import { Suspense, lazy, useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { errorMessage } from '../api/http'
import type { EntryDetail } from '../api/dataEntries'
import type { TagSuggestion } from '../api/dataLists'
import { TagInput } from './TagInput'
import { useCreateEntry, useUpdateEntry } from './queries'

/**
 * ProseMirror is ~130KB gzipped, so the editor stays out of the main bundle -
 * the same `lazy` + `Suspense` pattern ItemForm uses. A collection asks for it
 * more often than a list does, which 0006's consequence 7 accepts.
 */
const DetailEditor = lazy(() =>
  import('../items/DetailEditor').then((module) => ({ default: module.DetailEditor })),
)

/**
 * Adding or editing an entry (R51, R52).
 *
 * Unlike an item's add form there is no collapsed mode: an entry is three
 * fields and a detail, all of them optional but the name, so there is nothing
 * to hide behind a disclosure.
 */
export function EntryForm({
  listId,
  entry,
  suggestions,
  open,
  onClose,
}: {
  listId: string
  /** Absent when adding. */
  entry?: EntryDetail
  suggestions: TagSuggestion[]
  open: boolean
  onClose: () => void
}) {
  const [name, setName] = useState(entry?.name ?? '')
  const [description, setDescription] = useState(entry?.description ?? '')
  const [detailHtml, setDetailHtml] = useState(entry?.detailHtml ?? '')
  const [tags, setTags] = useState<string[]>(entry?.tags ?? [])
  const [problem, setProblem] = useState<string | null>(null)

  const create = useCreateEntry()
  const update = useUpdateEntry()
  const saving = create.isPending || update.isPending

  async function save() {
    setProblem(null)
    const body = {
      name: name.trim(),
      description: description.trim() || undefined,
      detailHtml: detailHtml || null,
      tags,
    }
    try {
      if (entry) {
        await update.mutateAsync({ id: entry.id, version: entry.version, ...body })
      } else {
        await create.mutateAsync({ listId, ...body })
      }
      onClose()
    } catch (error) {
      // Including the 409: the server's message names what happened, and
      // nothing is retried behind the member's back (R60).
      setProblem(errorMessage(error, 'That could not be saved.'))
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{entry ? 'Edit entry' : 'New entry'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            label="Name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            autoFocus
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            helperText="One line about it, if it needs one."
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <TagInput value={tags} suggestions={suggestions} onChange={setTags} />
          <Suspense fallback={<Skeleton variant="rounded" height={160} />}>
            <DetailEditor
              value={detailHtml}
              required={false}
              onChange={setDetailHtml}
              helperText="Ingredients, directions, notes, pictures. Paste or drop an image straight in."
            />
          </Suspense>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving || !name.trim()}>
          {entry ? 'Save' : 'Add'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
