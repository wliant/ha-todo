import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import { useCurrentUser } from '../auth/useCurrentUser'
import { DEFAULT_ICON } from '../lists/listIconTokens'
import { ListForm, type ListFormValue } from '../lists/ListForm'
import { useMembers } from '../lists/queries'
import { useCreateCollection } from '../data/queries'

/**
 * A route rather than a dialog, for the reason CreateListPage is one: it is
 * deep-linkable and a form this size is a bad dialog on a phone.
 *
 * {@link ListForm} is reused outright - naming a thing and choosing who sees it
 * is the same job here, and two forms describing it differently is how the two
 * features start to feel like different products. One call, not two: a
 * collection has no field configuration to save afterwards (R50).
 */
export function CreateCollectionPage() {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const createCollection = useCreateCollection()

  const [value, setValue] = useState<ListFormValue>({
    name: '',
    description: '',
    icon: DEFAULT_ICON,
    // Private by default, as a list is: the "my own vocabulary log" case.
    memberIds: [],
  })

  const others = (members ?? []).filter((member) => member.id !== user?.id)

  async function create() {
    try {
      const collection = await createCollection.mutateAsync({
        name: value.name.trim(),
        description: value.description.trim() || undefined,
        icon: value.icon,
        memberIds: value.memberIds,
      })
      navigate(`/collections/${collection.id}`)
    } catch {
      // Rendered from the mutation's own error state, below.
    }
  }

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        New collection
      </Typography>
      <Typography color="text.secondary">
        For what you look up again rather than what you finish - recipes, restaurants,
        quotes, words.
      </Typography>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <ListForm
          value={value}
          onChange={setValue}
          members={others}
          ownerName={user?.displayName ?? 'You'}
        />
      </Paper>

      {createCollection.isError && (
        <Alert severity="error">
          {errorMessage(createCollection.error, 'That could not be created.')}
        </Alert>
      )}

      <Stack direction="row" spacing={1}>
        <Button
          variant="contained"
          onClick={create}
          disabled={!value.name.trim() || createCollection.isPending}
        >
          Create
        </Button>
        <Button component={RouterLink} to="/collections">
          Cancel
        </Button>
      </Stack>
    </Stack>
  )
}
