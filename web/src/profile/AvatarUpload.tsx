import { useRef, useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import { MemberAvatar } from '../app/MemberAvatar'
import type { CurrentUser } from '../api/auth'
import { useOwnAvatar } from './useOwnAvatar'

/** Five megabytes, matching the server and nginx. */
const MAX_BYTES = 5 * 1024 * 1024

const ACCEPTED = 'image/png,image/jpeg,image/webp'

export function AvatarUpload({ user }: { user: CurrentUser }) {
  const input = useRef<HTMLInputElement>(null)
  const { upload, remove, isPending, error } = useOwnAvatar()
  const [refused, setRefused] = useState<string | null>(null)

  function choose(file: File | undefined) {
    setRefused(null)
    if (!file) {
      return
    }
    // Checked here only to save a pointless round trip with a large file on a
    // phone connection. The server decides, and decides from the bytes.
    if (file.size > MAX_BYTES) {
      setRefused('That picture is too large - 5 MB at most.')
      return
    }
    upload(file)
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <MemberAvatar
          displayName={user.displayName}
          avatarImageId={user.avatarImageId}
          size={72}
        />
        <Stack spacing={1}>
          <Typography color="text.secondary">
            Shown wherever you are named, including on the sign-in screen.
          </Typography>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
            <Button
              variant="outlined"
              disabled={isPending}
              onClick={() => input.current?.click()}
            >
              {user.avatarImageId ? 'Change picture' : 'Add a picture'}
            </Button>
            {user.avatarImageId && (
              <Button variant="text" disabled={isPending} onClick={() => remove()}>
                Remove
              </Button>
            )}
          </Stack>
        </Stack>
      </Stack>

      {/* Hidden because the styled button above is the control; the label is
          what keeps it reachable to anything that is not a mouse. */}
      <input
        ref={input}
        type="file"
        accept={ACCEPTED}
        aria-label="Profile picture"
        style={{ display: 'none' }}
        onChange={(event) => {
          choose(event.target.files?.[0])
          // Lets the same file be chosen again after a refusal.
          event.target.value = ''
        }}
      />

      {refused && <Alert severity="warning">{refused}</Alert>}
      {error && (
        <Alert severity="error">
          {errorMessage(error, 'Could not save that picture.')}
        </Alert>
      )}
    </Stack>
  )
}
