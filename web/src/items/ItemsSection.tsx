import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import HowToRegRounded from '@mui/icons-material/HowToRegRounded'
import { errorMessage, isConflict } from '../api/http'
import type { Item, ItemStatus, SaveItem } from '../api/items'
import type { ListDetail, ListField } from '../api/lists'
import { useCurrentUser } from '../auth/useCurrentUser'
import { CompletionNoteDialog } from './CompletionNoteDialog'
import { ItemForm } from './ItemForm'
import { ItemRow } from './ItemRow'
import {
  useAssignItems,
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
  // One id, so "at most one row expanded" (R38) is true by construction rather
  // than by rows coordinating with one another.
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [noting, setNoting] = useState<Item | null>(null)
  // Expanded while adding, so the collapsed form can show only what the list
  // insists on (R39).
  const [showAllFields, setShowAllFields] = useState(false)

  const { data: me } = useCurrentUser()
  const { data: items, isPending } = useItems(list.id, status)
  const create = useCreateItem(list.id)
  const update = useUpdateItem(list.id)
  const remove = useDeleteItem(list.id)
  const toggle = useToggleCompletion(list.id, setConflict)
  const assign = useAssignItems(list.id)

  const saving = create.isPending || update.isPending
  const saveError = create.error ?? update.error

  // Derived rather than pruned in an effect: ids that have gone - another
  // member deleted one, or the status filter changed - simply stop counting,
  // so nothing stale can be assigned and there is no second render to do it.
  const chosen = (items ?? []).filter((item) => selected.has(item.id))

  function save() {
    if (!editing) return
    const done = { onSuccess: () => setEditing(null) }
    if (editing.id) {
      update.mutate({ ...editing, id: editing.id }, done)
    } else {
      create.mutate(editing, done)
    }
  }

  function select(id: string, isSelected: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (isSelected) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
  }

  function assignToMe() {
    if (!me || chosen.length === 0) {
      return
    }
    assign.mutate(
      {
        assigneeId: me.id,
        items: chosen.map((item) => ({ id: item.id, version: item.version })),
      },
      {
        onSuccess: (result) => {
          // The ones that landed leave the selection; the ones that did not
          // stay, now carrying fresh versions, so a second tap retries exactly
          // those (adr/0026).
          const missed = new Set(result.conflicts.map((entry) => entry.id))
          setSelected(missed)
          if (result.conflicts.length > 0) {
            setConflict(
              result.assigned.length > 0
                ? `Took ${result.assigned.length}. ${result.conflicts.length} had already changed.`
                : result.conflicts[0].error,
            )
          }
        },
        onError: (error) =>
          setConflict(errorMessage(error, 'Could not take those items.')),
      },
    )
  }

  function startAdding() {
    setShowAllFields(false)
    setEditing(blank())
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
          <Button variant="contained" startIcon={<AddRounded />} onClick={startAdding}>
            Add item
          </Button>
        </Stack>
      </Stack>

      {chosen.length > 0 && (
        <Paper variant="outlined" sx={{ p: 1, bgcolor: 'action.hover' }}>
          <Stack
            direction="row"
            spacing={1}
            sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}
          >
            <Typography role="status" variant="body2" sx={{ flex: 1 }}>
              {chosen.length} selected
            </Typography>
            <Button
              size="small"
              variant="contained"
              startIcon={<HowToRegRounded />}
              disabled={assign.isPending || !me}
              onClick={assignToMe}
            >
              Assign to me
            </Button>
            <Button size="small" onClick={() => setSelected(new Set())}>
              Clear
            </Button>
          </Stack>
        </Paper>
      )}

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
            expanded={expandedId === item.id}
            selected={selected.has(item.id)}
            onToggleExpand={() => setExpandedId(expandedId === item.id ? null : item.id)}
            onSelect={(isSelected) => select(item.id, isSelected)}
            onComplete={() => toggle.mutate({ item, completed: true })}
            onWriteNote={() => setNoting(item)}
            onReopen={() => toggle.mutate({ item, completed: false })}
            onEdit={() => {
              setShowAllFields(true)
              setEditing(fromItem(item))
            }}
          />
        ))}
      </Stack>

      {noting && (
        <CompletionNoteDialog
          // Keyed on the item, so the editor is a fresh one each time: its
          // `value` is initial content and never re-syncs, and a reused
          // instance would show the previous item's note.
          key={noting.id}
          description={noting.description}
          onCancel={() => setNoting(null)}
          onComplete={(noteHtml) => {
            toggle.mutate({ item: noting, completed: true, noteHtml })
            setNoting(null)
          }}
        />
      )}

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
                expanded={showAllFields}
                onExpand={() => setShowAllFields(true)}
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
