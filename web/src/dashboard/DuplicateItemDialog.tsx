import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { DashboardItem } from '../api/dashboard'
import type { SaveItem } from '../api/items'
import { errorMessage, isConflict } from '../api/http'
import { duplicateOf } from '../items/duplicate'
import { ItemForm } from '../items/ItemForm'
import { useList, useListFields } from '../lists/queries'
import { useCreateItemFromAgenda } from './queries'

/**
 * Adding another chore like this one, without leaving the agenda (0018 R156).
 *
 * <p>**It goes on the same list** (R157). That is what makes this reachable
 * from here at all: the agenda crosses lists and has no notion of which one a
 * new item belongs on, so "duplicate" - whose answer is "the one it came from"
 * - is the only create this surface can offer without asking a question the
 * member did not come here to answer.
 *
 * <p>Which means the list's own form, from the list's own configuration. The
 * agenda's payload carries no fields and no members (`DashboardItem` is what a
 * grid cell needs), so the two are fetched here, keyed under `['lists']` and
 * therefore shared with the list screen's cache. Three states, not two: a
 * form drawn from `fields ?? []` would ask for a description alone while the
 * request was in flight, and a member who typed into it would lose whatever
 * the list actually insists on.
 */
export function DuplicateItemDialog({
  item,
  onClose,
  onCreated,
}: {
  item: DashboardItem
  onClose: () => void
  /** What the page's snackbar says once the item is really there. */
  onCreated: (message: string) => void
}) {
  // Seeded once, from the item that was duplicated: the form owns the value
  // from here on, and re-deriving it would undo every keystroke.
  const [value, setValue] = useState<SaveItem>(() => duplicateOf(item))
  const { data: list, isPending: listPending } = useList(item.listId)
  const { data: fields, isPending: fieldsPending, error: fieldsError } = useListFields(item.listId)
  const create = useCreateItemFromAgenda(item.listId)

  const loading = listPending || fieldsPending
  const ready = Boolean(list && fields)

  function save() {
    create.mutate(value, {
      onSuccess: () => {
        onCreated(`Added “${value.description}” to ${item.listName}.`)
        onClose()
      },
    })
  }

  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle sx={{ pb: 1 }}>Duplicate item</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            A copy of “{item.description}”, on {item.listName}. Its date, its assignee and its
            costs start empty.
          </Typography>

          {loading && <Skeleton variant="rounded" height={180} />}
          {fieldsError && (
            <Alert severity="error">{errorMessage(fieldsError, 'Could not read that list.')}</Alert>
          )}

          {ready && (
            /* Expanded, as the list's duplicate is: the writing is already
               copied, so what is left to answer is exactly what a collapsed
               form would hide (R155). */
            <ItemForm
              fields={fields!}
              members={list!.members}
              value={value}
              onChange={setValue}
              expanded
            />
          )}

          {create.error && (
            <Alert severity={isConflict(create.error) ? 'warning' : 'error'}>
              {errorMessage(create.error, 'Could not save that item.')}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!ready || create.isPending || !value.description.trim()}
          onClick={save}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  )
}
