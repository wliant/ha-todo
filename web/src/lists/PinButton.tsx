import IconButton from '@mui/material/IconButton'
import StarRounded from '@mui/icons-material/StarRounded'
import StarBorderRounded from '@mui/icons-material/StarBorderRounded'

/**
 * The star that pins a list or a collection (R27, and 0012 R104 for
 * collections).
 *
 * <p>Takes the toggle rather than reaching for a mutation of its own: the two
 * kinds have separate endpoints and separate query hierarchies (adr/0029), and
 * a button that picked between two hooks would have to call both on every
 * render to keep hook order stable. The caller already knows which thing it is
 * drawing.
 *
 * <p>Pinning is personal: one member's pins do not affect another's.
 */
export function PinButton({
  name,
  pinned,
  onToggle,
}: {
  name: string
  pinned: boolean
  onToggle: (pinned: boolean) => void
}) {
  return (
    <IconButton
      aria-label={pinned ? `Unpin ${name}` : `Pin ${name}`}
      onClick={(event) => {
        // The whole card is a link; pinning is not navigation.
        event.preventDefault()
        event.stopPropagation()
        onToggle(!pinned)
      }}
    >
      {pinned ? <StarRounded color="primary" /> : <StarBorderRounded />}
    </IconButton>
  )
}
