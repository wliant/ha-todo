import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import HowToRegRounded from '@mui/icons-material/HowToRegRounded'
import { errorMessage, isConflict } from '../api/http'
import type { Item, SaveItem } from '../api/items'
import type { ListDetail, ListField } from '../api/lists'
import { useCurrentUser } from '../auth/useCurrentUser'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { now } from '../dashboard/calendar'
import { CompletionNoteDialog } from './CompletionNoteDialog'
import { FilterBar } from './FilterBar'
import {
  DEFAULT_FILTER,
  isFiltered,
  matchesFilter,
  parseFilter,
  visibleItems,
  writeFilter,
  type ItemFilter,
} from './filters'
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
  // The filter lives in the address, not in state (adr/0045): a reload, a
  // navigation away and back, and a pasted link all show the same view, and
  // there is nothing here that can disagree with the URL.
  const [params, setParams] = useSearchParams()
  const zone = useHouseholdZone()
  const [editing, setEditing] = useState<(SaveItem & { id?: string }) | null>(null)
  // One snackbar for everything that happens after the fact: a conflict
  // somebody else caused, and a row leaving because it no longer matches the
  // filter (R119). The member's intent already happened either way.
  const [notice, setNotice] = useState<string | null>(null)
  // One id, so "at most one row expanded" (R38) is true by construction rather
  // than by rows coordinating with one another.
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [noting, setNoting] = useState<Item | null>(null)
  // Expanded while adding, so the collapsed form can show only what the list
  // insists on (R39).
  const [showAllFields, setShowAllFields] = useState(false)

  const { data: me } = useCurrentUser()
  const { data: items, isPending } = useItems(list.id)

  const shape = useMemo(
    () => ({ fields, memberIds: list.members.map((member) => member.id) }),
    [fields, list.members],
  )
  const filter = useMemo(() => parseFilter(params, shape), [params, shape])
  // `now(zone)` inside the memo rather than as a dependency: a `Civil` read
  // during render would change identity every time, and the due buckets only
  // move at midnight. The clock is reached through `calendar.ts` so that no
  // component here reads the device (adr/0036).
  const visible = useMemo(
    () => visibleItems(items ?? [], filter, fields, zone, now(zone)),
    [items, filter, fields, zone],
  )
  const filtered = isFiltered(filter)

  function applyFilter(next: ItemFilter) {
    // `replace`, so four taps on the bar are not four entries in the history
    // and leaving the list is one back press.
    setParams(writeFilter(next), { replace: true })
  }

  const create = useCreateItem(list.id)
  const update = useUpdateItem(list.id)
  const remove = useDeleteItem(list.id)
  const toggle = useToggleCompletion(list.id, setNotice)
  const assign = useAssignItems(list.id)

  /**
   * Completing something that the filter will then hide (R119).
   *
   * <p>The optimistic write flips `completed`, so with the default To do filter
   * the row goes at once. The row vanishing cannot be the only feedback - the
   * same rule the agenda follows (0010 R87, adr/0039).
   */
  function complete(item: Item, noteHtml?: string | null) {
    const surviving = matchesFilter(
      { ...item, completed: true },
      filter,
      fields,
      zone,
      now(zone),
    )
    toggle.mutate({ item, completed: true, noteHtml })
    if (!surviving) {
      setNotice(`Finished “${item.description}”.`)
    }
  }
  const saving = create.isPending || update.isPending
  const saveError = create.error ?? update.error

  // Derived rather than pruned in an effect: ids that have gone - another
  // member deleted one, or the filter stopped matching it - simply stop
  // counting, so nothing stale can be assigned and there is no second render
  // to do it. Derived from what is *drawn*, so "4 selected" is always four
  // rows the member can see (R121).
  const chosen = visible.filter((item) => selected.has(item.id))

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
            setNotice(
              result.assigned.length > 0
                ? `Took ${result.assigned.length}. ${result.conflicts.length} had already changed.`
                : result.conflicts[0].error,
            )
          }
        },
        onError: (error) =>
          setNotice(errorMessage(error, 'Could not take those items.')),
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
        <Button variant="contained" startIcon={<AddRounded />} onClick={startAdding}>
          Add item
        </Button>
      </Stack>

      <FilterBar
        filter={filter}
        fields={fields}
        members={list.members}
        onChange={applyFilter}
      />

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

      {/* Three states, not two (R117). A filtered list that matches nothing is
          not an empty list, and saying "Nothing to do here" about somebody's
          own list is a confident statement that is false - the same mistake
          the sidebar's groups made with `data ?? []`. */}
      {items && visible.length === 0 && (
        <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
          <Typography role="status" color="text.secondary">
            {filtered ? 'Nothing matches these filters.' : 'Nothing to do here.'}
          </Typography>
          {filtered && (
            <Button size="small" onClick={() => applyFilter(DEFAULT_FILTER)}>
              Clear filters
            </Button>
          )}
        </Stack>
      )}

      <Stack spacing={1}>
        {visible.map((item) => (
          <ItemRow
            key={item.id}
            item={item}
            fields={fields}
            expanded={expandedId === item.id}
            selected={selected.has(item.id)}
            onToggleExpand={() => setExpandedId(expandedId === item.id ? null : item.id)}
            onSelect={(isSelected) => select(item.id, isSelected)}
            onComplete={() => complete(item)}
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
            complete(noting, noteHtml)
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
        open={notice !== null}
        autoHideDuration={6000}
        onClose={() => setNotice(null)}
        message={notice ?? ''}
      />
    </Stack>
  )
}
