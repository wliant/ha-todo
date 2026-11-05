import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { fetchList, type ListDetail, type ListField } from '../api/lists'
import { errorMessage, isConflict, isNotFound } from '../api/http'
import { useCurrentUser } from '../auth/useCurrentUser'
import { describeProblem } from '../lists/fieldConfig'
import { FieldConfigEditor } from '../lists/FieldConfigEditor'
import { ListForm, type ListFormValue } from '../lists/ListForm'
import {
  useDeleteList,
  useList,
  useListFields,
  useMembers,
  useSaveListFields,
  useUpdateList,
  useUpdateListAccess,
} from '../lists/queries'

/** Renaming, re-icon-ing, re-sharing and deleting. Owner only (R20). */
export function ListSettingsPage() {
  const { listId } = useParams()
  const { data: list, isPending, error, refetch } = useList(listId)
  const { data: fields, isPending: fieldsPending } = useListFields(listId)
  // Bumped only when the member asks to reload after a conflict. Keying the
  // form on the list's *version* instead would remount it on every save -
  // saving invalidates, which refetches a new version - and the remount
  // interrupts the save it was triggered by.
  const [reloads, setReloads] = useState(0)

  if (isPending || fieldsPending) {
    return <Typography role="status">Loading…</Typography>
  }

  if (error || !list) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Alert severity={isNotFound(error) ? 'info' : 'error'}>
          {isNotFound(error)
            ? 'That list no longer exists, or was never shared with you.'
            : 'Could not load that list.'}
        </Alert>
        <Button component={RouterLink} to="/lists" variant="text">
          Back to lists
        </Button>
      </Stack>
    )
  }

  if (!list.owned) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Alert severity="info">Only the person who created this list can change it.</Alert>
        <Button component={RouterLink} to={`/lists/${list.id}`} variant="text">
          Back to the list
        </Button>
      </Stack>
    )
  }

  // Keyed so that an explicit reload remounts the form on the server's truth.
  // That is what lets the fields start from the fetched list without an effect
  // copying it into state after the fact.
  return (
    <Settings
      key={`${list.id}:${reloads}`}
      list={list}
      initialFields={fields ?? []}
      onReload={() => {
        setReloads((count) => count + 1)
        void refetch()
      }}
    />
  )
}

function Settings({
  list,
  initialFields,
  onReload,
}: {
  list: ListDetail
  initialFields: ListField[]
  onReload: () => void
}) {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const update = useUpdateList(list.id)
  const updateAccess = useUpdateListAccess(list.id)
  const saveFields = useSaveListFields()
  const remove = useDeleteList()

  const [value, setValue] = useState<ListFormValue>(() => ({
    name: list.name,
    description: list.description ?? '',
    icon: list.icon,
    memberIds: list.members.filter((member) => !member.owner).map((member) => member.id),
  }))
  // Only the optional fields are editable; the description is not a choice.
  const [fields, setFields] = useState<ListField[]>(() =>
    initialFields.filter((field) => field.kind !== 'DESCRIPTION'),
  )
  const [confirmDelete, setConfirmDelete] = useState(false)

  const problem = describeProblem(fields)
  const stale =
    isConflict(update.error) || isConflict(updateAccess.error) || isConflict(saveFields.error)
  const others = (members ?? []).filter((member) => member.id !== user?.id)

  async function save() {
    // Two writes, each carrying the version the member's view was drawn from.
    // The first moves it, so the second has to use the version it returned.
    // Awaited rather than nested in callbacks, so the navigation does not
    // depend on this component still being mounted when they finish.
    try {
      const updated = await update.mutateAsync({
        version: list.version,
        name: value.name.trim(),
        description: value.description.trim() || undefined,
        icon: value.icon,
      })
      await updateAccess.mutateAsync({
        version: updated.version,
        memberIds: value.memberIds,
      })
      // Every write moves the version, and only the first of the three returns
      // it. Re-reading is a round trip the alternative - teaching two more
      // endpoints to report a version their own payload has no use for - does
      // not obviously beat at this scale.
      const current = await fetchList(updated.id)
      await saveFields.mutateAsync({
        listId: updated.id,
        version: current.version,
        fields,
      })
      navigate(`/lists/${updated.id}`)
    } catch {
      // Rendered from the mutations' own error state, below.
    }
  }

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        List settings
      </Typography>

      {stale && (
        <Alert
          severity="warning"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => {
                update.reset()
                updateAccess.reset()
                saveFields.reset()
                onReload()
              }}
            >
              Reload
            </Button>
          }
        >
          Someone else changed this list while you were editing. Reload to see what it says
          now.
        </Alert>
      )}

      <Paper
        component="form"
        elevation={0}
        sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        <Stack spacing={2.5}>
          <ListForm value={value} onChange={setValue} members={others} ownerName="You" />

          <Divider />

          <FieldConfigEditor fields={fields} onChange={setFields} />

          {problem && <Alert severity="warning">{problem}</Alert>}

          {!stale && (update.isError || updateAccess.isError || saveFields.isError) && (
            <Alert severity="error">
              {errorMessage(
                update.error ?? updateAccess.error ?? saveFields.error,
                'Could not save that.',
              )}
            </Alert>
          )}

          <Stack direction="row" spacing={1}>
            <Button
              type="submit"
              variant="contained"
              disabled={
                update.isPending ||
                updateAccess.isPending ||
                saveFields.isPending ||
                !value.name.trim() ||
                problem !== null
              }
            >
              Save changes
            </Button>
            <Button component={RouterLink} to={`/lists/${list.id}`} variant="text">
              Cancel
            </Button>
          </Stack>
        </Stack>
      </Paper>

      <Paper
        component="section"
        elevation={0}
        sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
      >
        <Stack spacing={1.5} sx={{ alignItems: 'flex-start' }}>
          <Typography component="h2" variant="h6">
            Delete this list
          </Typography>
          <Typography color="text.secondary">
            It disappears for everyone it was shared with. There is no undo.
          </Typography>
          <Divider flexItem />
          <Button color="error" variant="outlined" onClick={() => setConfirmDelete(true)}>
            Delete {list.name}
          </Button>
          {remove.isError && (
            <Alert severity="error">
              {errorMessage(remove.error, 'Could not delete that list.')}
            </Alert>
          )}
        </Stack>
      </Paper>

      <Dialog open={confirmDelete} onClose={() => setConfirmDelete(false)}>
        <DialogTitle>Delete {list.name}?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Everyone it was shared with loses it too, and it cannot be brought back.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(false)}>Keep it</Button>
          <Button
            color="error"
            disabled={remove.isPending}
            onClick={() =>
              remove.mutate(list.id, { onSuccess: () => navigate('/lists', { replace: true }) })
            }
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
