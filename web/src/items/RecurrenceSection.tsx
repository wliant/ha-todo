import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded'
import { errorMessage } from '../api/http'
import type { ListDetail, ListField } from '../api/lists'
import { WEEKDAYS, type RecurrenceRule, type SaveRecurrenceRule } from '../api/recurrence'
import { RecurrenceForm } from './RecurrenceForm'
import {
  useCreateRule,
  useDeleteRule,
  useRules,
  useRunRules,
  useUpdateRule,
} from './recurrenceQueries'

function blank(): SaveRecurrenceRule {
  return {
    description: '',
    detailHtml: null,
    assigneeId: null,
    frequency: 'WEEKLY',
    intervalCount: 1,
    daysOfWeek: [],
    dayOfMonth: null,
    dueTime: null,
    leadDays: 0,
    startsOn: null,
    endsOn: null,
    paused: false,
    costValues: [],
  }
}

function fromRule(rule: RecurrenceRule): SaveRecurrenceRule & { id: string } {
  return {
    id: rule.id,
    version: rule.version,
    description: rule.description,
    detailHtml: rule.detailHtml,
    assigneeId: rule.assignee?.id ?? null,
    frequency: rule.frequency,
    intervalCount: rule.intervalCount,
    daysOfWeek: rule.daysOfWeek,
    dayOfMonth: rule.dayOfMonth,
    dueTime: rule.dueTime,
    leadDays: rule.leadDays,
    startsOn: rule.startsOn,
    endsOn: rule.endsOn,
    paused: rule.paused,
    costValues: rule.costValues,
  }
}

/**
 * What this list makes on its own (R41, R42).
 *
 * <p>Only the owner writes these - a standing instruction is as much the shape
 * of the list as its fields are - but anyone who can see the list can run them,
 * because a member who has just written one wants to watch it work.
 */
export function RecurrenceSection({
  list,
  fields,
}: {
  list: ListDetail
  fields: ListField[]
}) {
  const [editing, setEditing] = useState<(SaveRecurrenceRule & { id?: string }) | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const { data: rules, isPending } = useRules(list.id)
  const create = useCreateRule(list.id)
  const update = useUpdateRule(list.id)
  const remove = useDeleteRule(list.id)
  const run = useRunRules(list.id)

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
          Recurring
        </Typography>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Button
            size="small"
            startIcon={<PlayArrowRounded />}
            disabled={run.isPending || !rules || rules.length === 0}
            onClick={() =>
              run.mutate(undefined, {
                onSuccess: (result) =>
                  setNotice(
                    result.generated > 0
                      ? `Added ${result.generated}.`
                      : 'Nothing was due - everything is already on the list.',
                  ),
                onError: (error) =>
                  setNotice(errorMessage(error, 'Could not run these just now.')),
              })
            }
          >
            Run now
          </Button>
          {list.owned && (
            <Button
              variant="contained"
              startIcon={<AddRounded />}
              onClick={() => setEditing(blank())}
            >
              Add a recurring item
            </Button>
          )}
        </Stack>
      </Stack>

      <Typography variant="body2" color="text.secondary">
        These appear on the list by themselves. Editing one changes what it makes
        next time, not what it has already made.
      </Typography>

      {isPending && <Typography role="status">Loading recurring items…</Typography>}

      {rules && rules.length === 0 && (
        <Typography role="status" color="text.secondary">
          {list.owned
            ? 'Nothing recurs on this list yet.'
            : 'Nothing recurs on this list yet. Its owner sets these up.'}
        </Typography>
      )}

      <Stack spacing={1}>
        {rules?.map((rule) => (
          <Paper key={rule.id} variant="outlined" sx={{ p: 1.5 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
              <Stack spacing={0.5} sx={{ flex: 1, minWidth: 0 }}>
                <Typography sx={{ overflowWrap: 'anywhere' }}>{rule.description}</Typography>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                  <Chip size="small" variant="outlined" label={describe(rule)} />
                  {rule.dueTime && (
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`at ${rule.dueTime.slice(0, 5)}`}
                    />
                  )}
                  {rule.assignee && (
                    <Chip size="small" variant="outlined" label={rule.assignee.displayName} />
                  )}
                  {rule.paused && <Chip size="small" color="warning" label="Paused" />}
                </Stack>
                {rule.lastError && (
                  // A job nobody watches has to be able to explain itself.
                  <Alert severity="warning" sx={{ mt: 0.5 }}>
                    {rule.lastError}
                  </Alert>
                )}
              </Stack>
              {list.owned && (
                <IconButton
                  size="small"
                  aria-label={`Edit ${rule.description}`}
                  onClick={() => setEditing(fromRule(rule))}
                >
                  <EditRounded fontSize="small" />
                </IconButton>
              )}
            </Stack>
          </Paper>
        ))}
      </Stack>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editing?.id ? 'Edit a recurring item' : 'Add a recurring item'}</DialogTitle>
        <DialogContent>
          {editing && (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <RecurrenceForm
                fields={fields}
                members={list.members}
                value={editing}
                onChange={(next) => setEditing({ ...next, id: editing.id })}
              />
              {saveError && <Alert severity="error">{errorMessage(saveError, 'Could not save that.')}</Alert>}
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
            disabled={saving || !editing?.description.trim() || !scheduleReady(editing)}
            onClick={save}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={notice !== null}
        autoHideDuration={6000}
        onClose={() => setNotice(null)}
        message={notice ?? ''}
      />
    </Stack>
  )
}

/** The same rules the server enforces, so a member is told before a round trip. */
function scheduleReady(rule: (SaveRecurrenceRule & { id?: string }) | null): boolean {
  if (!rule) {
    return false
  }
  if (rule.frequency === 'WEEKLY') {
    return rule.daysOfWeek.length > 0
  }
  if (rule.frequency === 'MONTHLY') {
    return rule.dayOfMonth !== null && rule.dayOfMonth !== undefined
  }
  return true
}

function describe(rule: RecurrenceRule): string {
  const every = rule.intervalCount > 1 ? `every ${rule.intervalCount} ` : 'every '
  switch (rule.frequency) {
    case 'DAILY':
      return rule.intervalCount > 1 ? `${every}days` : 'every day'
    case 'WEEKLY': {
      const days = WEEKDAYS.filter((day) => rule.daysOfWeek.includes(day.value))
        .map((day) => day.short)
        .join(', ')
      const weeks = rule.intervalCount > 1 ? `${every}weeks on ` : ''
      return `${weeks}${days || 'no days yet'}`
    }
    case 'MONTHLY':
      return rule.intervalCount > 1
        ? `${every}months on the ${rule.dayOfMonth}`
        : `monthly on the ${rule.dayOfMonth}`
  }
}
