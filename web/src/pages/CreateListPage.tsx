import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import type { ListField } from '../api/lists'
import { useCurrentUser } from '../auth/useCurrentUser'
import { describeProblem } from '../lists/fieldConfig'
import { FieldConfigEditor } from '../lists/FieldConfigEditor'
import { DEFAULT_ICON } from '../lists/listIconTokens'
import { ListForm, type ListFormValue } from '../lists/ListForm'
import { useCreateList, useMembers, useSaveListFields } from '../lists/queries'

/**
 * A route rather than a dialog: it is deep-linkable, the sidebar's create
 * button can point at it, and a form this size is a bad dialog on a phone.
 */
export function CreateListPage() {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const createList = useCreateList()
  const [created, setCreated] = useState<{ id: string; version: number } | null>(null)
  const saveFields = useSaveListFields()

  // Only the optional fields live here. The description is the server's to
  // add, and it adds one to every list whether or not anyone asks.
  const [fields, setFields] = useState<ListField[]>([])
  const [value, setValue] = useState<ListFormValue>({
    name: '',
    description: '',
    icon: DEFAULT_ICON,
    // A private list is the safe default, and it is the "my own workout list"
    // case the requirement opens with.
    memberIds: [],
  })

  const others = (members ?? []).filter((member) => member.id !== user?.id)
  const problem = describeProblem(fields)
  const busy = createList.isPending || saveFields.isPending

  /**
   * Two calls: the list, then its configuration. The configuration write needs
   * the list's id and version, which only exist once it does - and the created
   * list is remembered so a failure here is retried rather than making a
   * second list.
   */
  async function create() {
    try {
      const list =
        created ??
        (await createList.mutateAsync({
          name: value.name.trim(),
          description: value.description.trim() || undefined,
          icon: value.icon,
          memberIds: value.memberIds,
        }))
      setCreated({ id: list.id, version: list.version })

      if (fields.length > 0) {
        await saveFields.mutateAsync({ listId: list.id, version: list.version, fields })
      }
      navigate(`/lists/${list.id}`)
    } catch {
      // Rendered from the mutations' own error state, below.
    }
  }

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
          void create()
        }}
      >
        <Stack spacing={2.5}>
          <ListForm
            value={value}
            onChange={setValue}
            members={others}
            ownerName="You"
          />

          <Divider />

          <FieldConfigEditor fields={fields} onChange={setFields} />

          {problem && <Alert severity="warning">{problem}</Alert>}

          {(createList.isError || saveFields.isError) && (
            <Alert severity="error">
              {errorMessage(
                createList.error ?? saveFields.error,
                'Could not create that list.',
              )}
            </Alert>
          )}

          <Stack direction="row" spacing={1}>
            <Button
              type="submit"
              variant="contained"
              disabled={busy || !value.name.trim() || problem !== null}
            >
              {busy ? 'Creating…' : 'Create list'}
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
