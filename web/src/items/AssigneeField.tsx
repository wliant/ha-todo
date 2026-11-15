import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import FormLabel from '@mui/material/FormLabel'
import Stack from '@mui/material/Stack'
import type { ListMember } from '../api/lists'
import { MemberAvatar } from '../app/MemberAvatar'

/**
 * Who a chore is for, in one tap (0017 R150).
 *
 * <p>A row of chips rather than a `select`, collapsed and expanded alike: the
 * old control cost two taps and a popup over the form to choose between four
 * names that fit on one line. Same selected/unselected vocabulary as the date
 * chips beside it - filled primary against outlined - because the two rows are
 * one idea (adr/0050).
 *
 * <p>Only the people who can see this list (R24). The server checks it too;
 * this is the convenience, not the control.
 */
export function AssigneeField({
  members,
  required,
  value,
  onChange,
}: {
  members: ListMember[]
  required: boolean
  value: string | null | undefined
  onChange: (assigneeId: string | null) => void
}) {
  const selected = value ?? null

  return (
    <Box>
      <FormLabel component="legend" required={required} sx={{ fontSize: '0.75rem' }}>
        Assignee
      </FormLabel>

      <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
        {/* Mirrors the dropdown's "Nobody", and makes unassigned a visible
            selection rather than the absence of one. Tapping a selected member
            deliberately does nothing: one obvious way to unassign beats two,
            and a toggle-off gesture on a chip that looks like every other chip
            is not discoverable anyway. */}
        {!required && (
          <Chip
            label="Nobody"
            size="small"
            aria-pressed={selected === null}
            color={selected === null ? 'primary' : 'default'}
            variant={selected === null ? 'filled' : 'outlined'}
            onClick={() => onChange(null)}
          />
        )}
        {members.map((member) => (
          <Chip
            key={member.id}
            label={member.displayName}
            size="small"
            // A row of toggles whose state lives only in colour is unreadable
            // to anything that is not looking at it.
            aria-pressed={selected === member.id}
            color={selected === member.id ? 'primary' : 'default'}
            variant={selected === member.id ? 'filled' : 'outlined'}
            // Decoration beside the name, never part of it: MemberAvatar is
            // aria-hidden, so the chip is still found by the member's name.
            avatar={
              <MemberAvatar
                displayName={member.displayName}
                avatarImageId={member.avatarImageId}
                size={24}
              />
            }
            onClick={() => onChange(member.id)}
          />
        ))}
      </Stack>
    </Box>
  )
}
