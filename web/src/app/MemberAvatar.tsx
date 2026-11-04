import Avatar from '@mui/material/Avatar'
import type { SxProps, Theme } from '@mui/material/styles'
import { imageUrl } from '../api/http'
import { initials } from './initials'

/**
 * A member's face, or their initials when they have not uploaded one.
 *
 * Always decorative: every place this appears, the member's name is already
 * written beside it, so letting the picture into the accessible name would
 * make rows read "A Ana" and break every by-name selector.
 */
export function MemberAvatar({
  displayName,
  avatarImageId,
  size,
  sx,
  fallback = 'initials',
}: {
  displayName: string
  avatarImageId?: string | null
  size?: number
  sx?: SxProps<Theme>
  /**
   * What to draw for a member with no picture. Initials everywhere except the
   * sign-in roster, where R17 says each row shows the person's name and
   * nothing else - and an initial is a second piece of text in the row, which
   * makes it read "A Ana" to anything matching on text.
   */
  fallback?: 'initials' | 'icon'
}) {
  const dimension = size ?? 40
  return (
    <Avatar
      aria-hidden
      src={imageUrl(avatarImageId)}
      sx={{ width: dimension, height: dimension, fontSize: dimension * 0.4, ...sx }}
    >
      {fallback === 'initials' ? initials(displayName) : null}
    </Avatar>
  )
}
