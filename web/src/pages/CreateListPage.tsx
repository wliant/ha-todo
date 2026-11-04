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
import { useCreateList, useMembers } from '../lists/queries'

/**
 * A route rather than a dialog: it is deep-linkable, the sidebar's create
 * button can point at it, and a form this size is a bad dialog on a phone.
 */
export function CreateListPage() {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const createList = useCreateList()

  const [value, setValue] = useState<ListFormValue>({
    name: '',
    description: '',
    icon: DEFAULT_ICON,
    // A private list is the safe default, and it is the "my own workout list"
    // case the requirement opens with.
    memberIds: [],
  })

  const others = (members ?? []).filter((member) => member.id !== user?.id)

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        New list
      </Typography>

      <Paper
        component="form"
        elevation={0}
        sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
        onSubmit={(event) => {
          event.preventDefault()
          createList.mutate(
            {
              name: value.name.trim(),
              description: value.description.trim() || undefined,
              icon: value.icon,
              memberIds: value.memberIds,
            },
            { onSuccess: (list) => navigate(`/lists/${list.id}`) },
          )
        }}
      >
        <Stack spacing={2.5}>
          <ListForm
            value={value}
            onChange={setValue}
            members={others}
            ownerName="You"
          />

          {createList.isError && (
            <Alert severity="error">
              {errorMessage(createList.error, 'Could not create that list.')}
            </Alert>
          )}

          <Stack direction="row" spacing={1}>
            <Button
              type="submit"
              variant="contained"
              disabled={createList.isPending || !value.name.trim()}
            >
              {createList.isPending ? 'Creating…' : 'Create list'}
            </Button>
            <Button component={RouterLink} to="/lists" variant="text">
              Cancel
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  )
}
