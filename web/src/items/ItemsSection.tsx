import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import { errorMessage, isConflict } from '../api/http'
import type { Item, ItemStatus, SaveItem } from '../api/items'
import type { ListDetail, ListField } from '../api/lists'
import { ItemForm } from './ItemForm'
import { ItemRow } from './ItemRow'
import {
  useCreateItem,
  useDeleteItem,
  useItems,
  useToggleCompletion,
  useUpdateItem,
} from './queries'

function blank(): SaveItem {
  return { description: '', detailHtml: null, assigneeId: null, dueAt: null, costValues: [] }
}

function fromItem(item: Item): SaveItem & { id: string } {
  return {
    id: item.id,
    version: item.version,
    description: item.description,
    detailHtml: item.detailHtml,
    assigneeId: item.assignee?.id ?? null,
    dueAt: item.dueAt,
    costValues: item.costValues,
  }
}

export function ItemsSection({ list, fields }: { list: ListDetail; fields: ListField[] }) {
  const [status, setStatus] = useState<ItemStatus>('open')
  const [editing, setEditing] = useState<(SaveItem & { id?: string }) | null>(null)
  const [conflict, setConflict] = useState<string | null>(null)

  const { data: items, isPending } = useItems(list.id, status)
  const create = useCreateItem(list.id)
  const update = useUpdateItem(list.id)
  const remove = useDeleteItem(list.id)
  const toggle = useToggleCompletion(list.id, setConflict)

  const saving = create.isPending || update.isPending
  const saveError = create.error ?? update.error

  function save() {
    if (!editing) return
    const done = { onSuccess: () => setEditing(null) }
    if (editing.id) {
      update.mutate({ ...editing, id: editing.id }, done)
    } else {
      create.mutate(editing, done)
    }
  }

  return (
    <Stack spacing={2}>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
      >
        <Typography component="h2" variant="h6">
          Items
        </Typography>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={status}
            onChange={(_event, next: ItemStatus | null) => next && setStatus(next)}
            aria-label="Which items"
          >
            <ToggleButton value="open">To do</ToggleButton>
            <ToggleButton value="done">Done</ToggleButton>
            <ToggleButton value="all">All</ToggleButton>
          </ToggleButtonGroup>
          <Button
            variant="contained"
            startIcon={<AddRounded />}
            onClick={() => setEditing(blank())}
          >
            Add item
          </Button>
        </Stack>
      </Stack>

      {isPending && <Typography role="status">Loading items…</Typography>}

      {items && items.length === 0 && (
        <Typography role="status" color="text.secondary">
          {status === 'done' ? 'Nothing finished yet.' : 'Nothing to do here.'}
        </Typography>
      )}

      <Stack spacing={1}>
        {items?.map((item) => (
          <ItemRow
            key={item.id}
            item={item}
            fields={fields}
            onToggle={(completed) => toggle.mutate({ item, completed })}
            onEdit={() => setEditing(fromItem(item))}
          />
        ))}
      </Stack>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editing?.id ? 'Edit item' : 'Add an item'}</DialogTitle>
        <DialogContent>
          {editing && (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <ItemForm
                fields={fields}
                members={list.members}
                value={editing}
                onChange={(next) => setEditing({ ...next, id: editing.id })}
              />
              {saveError && (
                <Alert severity={isConflict(saveError) ? 'warning' : 'error'}>
                  {errorMessage(saveError, 'Could not save that item.')}
                </Alert>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          {editing?.id && (
            <Button
              color="error"
              sx={{ mr: 'auto' }}
              onClick={() =>
                remove.mutate(
                  { id: editing.id!, version: editing.version! },
                  { onSuccess: () => setEditing(null) },
                )
              }
            >
              Delete
            </Button>
          )}
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={saving || !editing?.description.trim()}
            onClick={save}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      {/* A snackbar, not a dialog: the member's intent already happened, and
          there is nothing left for them to decide. */}
      <Snackbar
        open={conflict !== null}
        autoHideDuration={6000}
        onClose={() => setConflict(null)}
        message={conflict ?? ''}
      />
    </Stack>
  )
}
