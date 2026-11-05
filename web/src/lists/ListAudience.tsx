import AvatarGroup from '@mui/material/AvatarGroup'
import Tooltip from '@mui/material/Tooltip'
import Box from '@mui/material/Box'
import type { ListMember } from '../api/lists'
import { MemberAvatar } from '../app/MemberAvatar'

/**
 * Who can see this list, as a row of overlapping faces.
 *
 * <p>Deliberately not a presence indicator: these are the people the list was
 * shared with (R19), not the people looking at it right now. Knowing who has
 * it open would need a live channel this product does not have, and is still
 * an open question in requirements/0004.
 */
export function ListAudience({ members }: { members: ListMember[] }) {
  if (members.length === 0) {
    return null
  }

  return (
    <AvatarGroup
      max={5}
      // Reads left to right in the order the server sorted them, rather than
      // MUI's default of stacking the first on top.
      total={members.length}
      sx={{
        '& .MuiAvatar-root': {
          width: 28,
          height: 28,
          fontSize: '0.75rem',
          borderColor: 'background.default',
        },
      }}
    >
      {members.map((member) => (
        <Tooltip
          key={member.id}
          title={member.owner ? `${member.displayName} (owner)` : member.displayName}
        >
          {/* The tooltip needs a focusable, hoverable host, and the avatar
              itself is aria-hidden everywhere else it appears. */}
          <Box component="span" tabIndex={0} aria-label={member.displayName}>
            <MemberAvatar
              displayName={member.displayName}
              avatarImageId={member.avatarImageId}
              size={28}
            />
          </Box>
        </Tooltip>
      ))}
    </AvatarGroup>
  )
}
