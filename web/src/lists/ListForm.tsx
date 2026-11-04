import Checkbox from '@mui/material/Checkbox'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { ListIcon as ListIconToken, Member } from '../api/lists'
import { IconPicker } from './IconPicker'

export interface ListFormValue {
  name: string
  description: string
  icon: ListIconToken
  memberIds: string[]
}

/**
 * Naming a list and choosing its audience. Shared by creating and editing, so
 * the two cannot drift into describing the same thing differently.
 */
export function ListForm({
  value,
  onChange,
  members,
  ownerName,
}: {
  value: ListFormValue
  onChange: (value: ListFormValue) => void
  members: Member[]
  ownerName: string
}) {
  function toggle(id: string) {
    const next = value.memberIds.includes(id)
      ? value.memberIds.filter((member) => member !== id)
      : [...value.memberIds, id]
    onChange({ ...value, memberIds: next })
  }

  return (
    <Stack spacing={2.5}>
      <TextField
        label="Name"
        required
        value={value.name}
        onChange={(event) => onChange({ ...value, name: event.target.value })}
        slotProps={{ htmlInput: { maxLength: 80 } }}
      />

      <TextField
        label="Description"
        multiline
        minRows={2}
        value={value.description}
        onChange={(event) => onChange({ ...value, description: event.target.value })}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        helperText="Optional."
      />

      <IconPicker value={value.icon} onChange={(icon) => onChange({ ...value, icon })} />

      <Stack spacing={1}>
        <Typography component="h3" variant="subtitle2" color="text.secondary">
          Who can see it
        </Typography>

        {/* A plain checkbox list rather than a tag field: a household is a
            handful of people, so there is nothing to search, and every choice
            is visible at once with a real labelled control to toggle it. */}
        <Paper variant="outlined">
          <List dense disablePadding aria-label="Who can see it">
            {members.map((member) => {
              const checked = value.memberIds.includes(member.id)
              return (
                <ListItemButton
                  key={member.id}
                  onClick={() => toggle(member.id)}
                  role="checkbox"
                  aria-checked={checked}
                  sx={{ borderRadius: 0 }}
                >
                  {/* The row is the control - it carries the role, the state
                      and the name. The box is the picture of that state, and a
                      second focusable checkbox inside it would be one control
                      too many. */}
                  <ListItemIcon sx={{ minWidth: 40 }} aria-hidden>
                    <Checkbox edge="start" checked={checked} tabIndex={-1} disableRipple />
                  </ListItemIcon>
                  <ListItemText primary={member.displayName} />
                </ListItemButton>
              )
            })}
            {members.length === 0 && (
              <ListItemText
                sx={{ px: 2, py: 1.5 }}
                primary="Nobody else has an account yet."
              />
            )}
          </List>
        </Paper>

        {/* The owner is not a choice: they always hold access, and a list
            nobody can reach is not a state worth being able to reach. */}
        <Typography variant="body2" color="text.secondary">
          {ownerName} can always see this list. Leave the rest unticked to keep it private.
        </Typography>
      </Stack>
    </Stack>
  )
}
