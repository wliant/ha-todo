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
import { errorMessage, isNotFound } from '../api/http'
import type { CollectionDetail } from '../api/dataLists'
import { useCurrentUser } from '../auth/useCurrentUser'
import { ListForm, type ListFormValue } from '../lists/ListForm'
import { useMembers } from '../lists/queries'
import {
  useCollection,
  useDeleteCollection,
  useUpdateCollection,
  useUpdateCollectionAccess,
} from '../data/queries'

/** Renaming, re-icon-ing, re-sharing and deleting. Owner only (R50). */
export function CollectionSettingsPage() {
  const { listId } = useParams()
  const { data: collection, isPending, error, refetch } = useCollection(listId)
  const [reloads, setReloads] = useState(0)

  if (isPending) {
    return <Typography role="status">Loading…</Typography>
  }

  if (error || !collection) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Alert severity={isNotFound(error) ? 'info' : 'error'}>
          {isNotFound(error)
            ? 'That collection no longer exists, or was never shared with you.'
            : 'That could not be loaded.'}
        </Alert>
        <Button component={RouterLink} to="/collections">
          Back to collections
        </Button>
      </Stack>
    )
  }

  return (
    <Editor
      key={`${collection.id}-${reloads}`}
      collection={collection}
      onReload={() => {
        void refetch()
        setReloads((count) => count + 1)
      }}
    />
  )
}

function Editor({
  collection,
  onReload,
}: {
  collection: CollectionDetail
  onReload: () => void
}) {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const update = useUpdateCollection(collection.id)
  const updateAccess = useUpdateCollectionAccess(collection.id)
  const remove = useDeleteCollection()

  const [value, setValue] = useState<ListFormValue>({
    name: collection.name,
    description: collection.description ?? '',
    icon: collection.icon,
    memberIds: collection.members.filter((member) => !member.owner).map((member) => member.id),
  })
  const [problem, setProblem] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  const others = (members ?? []).filter((member) => member.id !== user?.id)
  const busy = update.isPending || updateAccess.isPending || remove.isPending

  /**
   * Two writes, in this order, each echoing the version the previous one
   * returned - the access rows are children, so changing them moves the
   * collection's version (markChanged) and a second write from the old one
   * would be refused.
   */
  async function save() {
    setProblem(null)
    try {
      const saved = await update.mutateAsync({
        version: collection.version,
        name: value.name.trim(),
        description: value.description.trim() || undefined,
        icon: value.icon,
      })
      await updateAccess.mutateAsync({
        version: saved.version,
        memberIds: value.memberIds,
      })
      navigate(`/collections/${collection.id}`)
    } catch (error) {
      // The server's own sentence, which already distinguishes a 403 ("only
      // the person who created this collection") from a 409 ("someone else
      // changed this"). A second copy of that wording here would drift.
      setProblem(errorMessage(error, 'That could not be saved.'))
    }
  }

  async function destroy() {
    setProblem(null)
    try {
      await remove.mutateAsync(collection.id)
      navigate('/collections')
    } catch (error) {
      setProblem(errorMessage(error, 'That could not be deleted.'))
    }
  }

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        Collection settings
      </Typography>

      {problem && (
        <Alert
          severity="warning"
          action={
            <Button size="small" onClick={onReload}>
              Reload
            </Button>
          }
        >
          {problem}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <ListForm
          value={value}
          onChange={setValue}
          members={others}
          ownerName={
            collection.members.find((member) => member.owner)?.displayName ?? 'The owner'
          }
        />
      </Paper>

      <Stack direction="row" spacing={1}>
        <Button variant="contained" onClick={save} disabled={busy || !value.name.trim()}>
          Save
        </Button>
        <Button component={RouterLink} to={`/collections/${collection.id}`}>
          Cancel
        </Button>
      </Stack>

      <Divider />

      <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Typography variant="h6" component="h2">
          Delete this collection
        </Typography>
        <Typography color="text.secondary">
          Everything in it goes too, including its pictures. There is no undo.
        </Typography>
        <Button color="error" onClick={() => setConfirming(true)} disabled={busy}>
          Delete collection
        </Button>
      </Stack>

      <Dialog open={confirming} onClose={() => setConfirming(false)}>
        <DialogTitle>Delete “{collection.name}”?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Every entry in it is deleted as well. This cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirming(false)}>Keep it</Button>
          <Button color="error" onClick={destroy} disabled={busy}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
