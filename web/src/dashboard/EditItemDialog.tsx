import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import type { DashboardItem } from '../api/dashboard'
import type { SaveItem } from '../api/items'
import { errorMessage, isConflict } from '../api/http'
import { editableOf } from '../items/edit'
import { ItemForm } from '../items/ItemForm'
import { useList, useListFields } from '../lists/queries'
import { useUpdateItemFromAgenda } from './queries'

/**
 * Changing a chore without leaving the agenda (0020 R173).
 *
 * <p>The agenda is where a member finds out that something is wrong with an
 * item - it is due on the wrong day, or it is nobody's. Until now the only
 * answer was "open the list", which is two navigations away from the thing
 * being looked at, and the date is the field most often wrong on the surface
 * that exists to show dates.
 *
 * <p>The list's own form, from the list's own configuration, for
 * `DuplicateItemDialog`'s reason: `DashboardItem` is what a grid cell needs
 * and carries no fields and no members, so both are fetched here under the
 * list's own cache keys. Three states, not two - a form drawn from
 * `fields ?? []` while the request is in flight asks for a description alone
 * and is indistinguishable from a failed request.
 *
 * <p>Expanded, unlike adding (0017): every answer the item already has is
 * worth seeing, and the whole point of opening this is usually one of the
 * fields a collapsed form would hide.
 */
export function EditItemDialog({
  item,
  onClose,
  onSaved,
}: {
  item: DashboardItem
  onClose: () => void
  /** What the page's snackbar says once the change is really stored. */
  onSaved: (message: string) => void
}) {
  // Seeded once, from the item as the agenda holds it - the version included,
  // so a save that lost a race is refused rather than overwriting whoever got
  // there first. Re-deriving it would undo every keystroke.
  const [value, setValue] = useState<SaveItem & { id: string }>(() => editableOf(item))
  const { data: list, isPending: listPending } = useList(item.listId)
  const { data: fields, isPending: fieldsPending, error: fieldsError } = useListFields(item.listId)
  const update = useUpdateItemFromAgenda()

  const loading = listPending || fieldsPending
  const ready = Boolean(list && fields)

  function save() {
    update.mutate(value, {
      onSuccess: () => {
        onSaved(`Saved “${value.description}”.`)
        onClose()
      },
    })
  }

  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle sx={{ pb: 1 }}>Edit item</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {loading && <Skeleton variant="rounded" height={180} />}
          {fieldsError && (
            <Alert severity="error">{errorMessage(fieldsError, 'Could not read that list.')}</Alert>
          )}

          {ready && (
            <ItemForm
              fields={fields!}
              members={list!.members}
              value={value}
              onChange={(next) => setValue({ ...next, id: value.id })}
              expanded
            />
          )}

          {update.error && (
            <Alert severity={isConflict(update.error) ? 'warning' : 'error'}>
              {/* A 409 names the other member, as it does on the list: the
                  form stays open holding what was typed, and nothing is
                  retried behind anybody's back. */}
              {errorMessage(update.error, 'Could not save that item.')}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!ready || update.isPending || !value.description.trim()}
          onClick={save}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  )
}
