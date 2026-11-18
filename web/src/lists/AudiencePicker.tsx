import Checkbox from '@mui/material/Checkbox'
import List from '@mui/material/List'
import ListItemAvatar from '@mui/material/ListItemAvatar'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { Member } from '../api/lists'
import { MemberAvatar } from '../app/MemberAvatar'

/**
 * Who else can see this thing.
 *
 * <p>Extracted from {@link ListForm} when trackers became the third kind to ask
 * it (0019 R164). The three features grant access the same way and the question
 * reads the same in all three, so three copies of this list would be three
 * places for the wording and the owner rule to drift apart.
 *
 * <p>What it is *about* differs, which is the one prop: a list, a collection or
 * a tracker.
 */
export function AudiencePicker({
  members,
  ownerName,
  selected,
  onChange,
  noun,
}: {
  members: Member[]
  ownerName: string
  selected: string[]
  onChange: (memberIds: string[]) => void
  /** "list", "collection", "tracker" - whatever the sentence below is about. */
  noun: string
}) {
  function toggle(id: string) {
    onChange(
      selected.includes(id)
        ? selected.filter((member) => member !== id)
        : [...selected, id],
    )
  }

  return (
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
            const checked = selected.includes(member.id)
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
                <ListItemAvatar aria-hidden sx={{ minWidth: 48 }}>
                  <MemberAvatar
                    displayName={member.displayName}
                    avatarImageId={member.avatarImageId}
                    size={32}
                  />
                </ListItemAvatar>
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

      {/* The owner is not a choice: they always hold access, and something
          nobody can reach is not a state worth being able to reach. */}
      <Typography variant="body2" color="text.secondary">
        {ownerName} can always see this {noun}. Leave the rest unticked to keep it private.
      </Typography>
    </Stack>
  )
}
