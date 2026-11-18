import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import type { ListIcon as ListIconToken, Member } from '../api/lists'
import { AudiencePicker } from './AudiencePicker'
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

      <AudiencePicker
        members={members}
        ownerName={ownerName}
        selected={value.memberIds}
        onChange={(memberIds) => onChange({ ...value, memberIds })}
        noun="list"
      />
    </Stack>
  )
}
