import Checkbox from '@mui/material/Checkbox'
import Divider from '@mui/material/Divider'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import MenuItem from '@mui/material/MenuItem'
import { MenuChip } from '../app/MenuChip'
import { ListIcon } from '../lists/listIcons'
import { useLists } from '../lists/queries'
import { listFilterLabel } from './listFilter'

/**
 * Which lists the agenda is showing (0014 R123).
 *
 * <p>`useLists({})` rather than the sidebar's: that one is capped at the
 * member's preference and partitioned against the pinned section, so it is
 * deliberately *not* the full set - and a filter that cannot offer every list
 * is a filter that hides work without saying so.
 *
 * <p>Always rendered, including while the lists load and when nothing is
 * chosen (R124): an agenda showing less than it holds must never look like an
 * agenda that holds that little.
 */
export function ListFilterChip({
  picked,
  onChange,
}: {
  picked: string[]
  onChange: (ids: string[]) => void
}) {
  // The menu's options. The page reads the same query to narrow `picked` to
  // what this member can actually see (R128), so it arrives here already
  // trimmed and there is one owner of that rule.
  const { data: lists } = useLists({})

  function toggle(id: string) {
    onChange(picked.includes(id) ? picked.filter((each) => each !== id) : [...picked, id])
  }

  return (
    <MenuChip label={listFilterLabel(picked, lists)} active={picked.length > 0}>
      {(close) => [
        // Closes, where a tick does not: clearing is a finished decision, and
        // leaving the menu open over a restored agenda hides the thing the
        // member just asked to see.
        <MenuItem
          key="all"
          selected={picked.length === 0}
          onClick={() => {
            onChange([])
            close()
          }}
        >
          <ListItemText primary="All lists" />
        </MenuItem>,
        <Divider key="divider" />,
        // The menu stays open on a tick, because picking three lists is one
        // gesture and reopening the chip between each is three.
        ...(lists ?? []).map((list) => (
          <MenuItem key={list.id} onClick={() => toggle(list.id)}>
            <Checkbox checked={picked.includes(list.id)} size="small" tabIndex={-1} />
            <ListItemIcon>
              <ListIcon token={list.icon} fontSize="small" />
            </ListItemIcon>
            <ListItemText primary={list.name} />
          </MenuItem>
        )),
      ]}
    </MenuChip>
  )
}
