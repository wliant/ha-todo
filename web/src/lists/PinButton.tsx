import IconButton from '@mui/material/IconButton'
import StarRounded from '@mui/icons-material/StarRounded'
import StarBorderRounded from '@mui/icons-material/StarBorderRounded'
import { usePinList } from './queries'

/** Pinning is personal (R27): one member's pins do not affect another's. */
export function PinButton({
  id,
  name,
  pinned,
}: {
  id: string
  name: string
  pinned: boolean
}) {
  const pin = usePinList()
  return (
    <IconButton
      aria-label={pinned ? `Unpin ${name}` : `Pin ${name}`}
      onClick={(event) => {
        // The whole card is a link; pinning is not navigation.
        event.preventDefault()
        event.stopPropagation()
        pin.mutate({ id, pinned: !pinned })
      }}
    >
      {pinned ? <StarRounded color="primary" /> : <StarBorderRounded />}
    </IconButton>
  )
}
