import { useState, type ReactNode } from 'react'
import Chip from '@mui/material/Chip'
import Menu from '@mui/material/Menu'

/**
 * A chip that opens a menu.
 *
 * <p>Filled when its filter is narrowing what is on screen and outlined when
 * it is not, so a bar of them reads at a glance - the colour is never the only
 * cue, because the chip's own label names the selection.
 *
 * <p>Here rather than inside a filter bar because two surfaces draw one: the
 * list's (0013 R107) and the agenda's (0014 R123). A component file that also
 * exported helpers would break fast refresh, so this exports nothing else.
 */
export function MenuChip({
  label,
  active,
  children,
}: {
  label: string
  active: boolean
  children: (close: () => void) => ReactNode
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const close = () => setAnchor(null)
  return (
    <>
      <Chip
        label={label}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        color={active ? 'primary' : 'default'}
        variant={active ? 'filled' : 'outlined'}
        onClick={(event) => setAnchor(event.currentTarget)}
        sx={{ flexShrink: 0 }}
      />
      <Menu anchorEl={anchor} open={anchor !== null} onClose={close}>
        {children(close)}
      </Menu>
    </>
  )
}
