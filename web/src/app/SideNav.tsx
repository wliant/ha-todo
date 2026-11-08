import Box from '@mui/material/Box'
import Collapse from '@mui/material/Collapse'
import Divider from '@mui/material/Divider'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Stack from '@mui/material/Stack'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import ChecklistRounded from '@mui/icons-material/ChecklistRounded'
import CollectionsBookmarkRounded from '@mui/icons-material/CollectionsBookmarkRounded'
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import PushPinRounded from '@mui/icons-material/PushPinRounded'
import TodayRounded from '@mui/icons-material/TodayRounded'
import GroupRounded from '@mui/icons-material/GroupRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import IconButton from '@mui/material/IconButton'
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import type { CurrentUser } from '../api/auth'
import type { ListIcon as ListIconToken } from '../api/lists'
import { ListIcon } from '../lists/listIcons'
import { useSidebar } from '../lists/queries'
import { useCollectionSidebar } from '../data/queries'
import { MemberAvatar } from './MemberAvatar'

/**
 * The one navigation surface (R25), now reaching every list and collection the
 * member can see (0012 R98-R103).
 *
 * <p>The old Pinned and Recent sections are gone. They showed only lists with a
 * recorded interaction, so a list just created - and every list somebody else
 * shared - was absent until it had been opened once, which is the opposite of
 * what a shortcut is for. Lists and Collections are now expandable groups in
 * the menu, each ordered by the member's own use of it over the last month
 * (adr/0041), each capped at their own preference.
 *
 * <p>Which groups are open lives here, in this component's own state - see
 * adr/0042 for why that does not reopen adr/0015's rule: a group the member
 * collapsed is something they did, not where something sits.
 */
export function SideNav({
  user,
  onNavigate,
  onSignOut,
}: {
  user: CurrentUser
  onNavigate?: () => void
  onSignOut: () => void
}) {
  const { pathname } = useLocation()
  const { data: sidebar } = useSidebar()
  const { data: collections } = useCollectionSidebar()

  // Both open by default: these groups replace the Pinned and Recent sections
  // wholesale, so a sidebar that starts collapsed offers strictly less than the
  // one it replaced. Not persisted - SideNav mounts once inside AppShell and
  // the mobile drawer is keepMounted, so a collapse survives every navigation
  // within the session, which is the whole span anybody would notice.
  const [open, setOpen] = useState({ lists: true, collections: true })

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2 }}>
        <Typography component="span" sx={{ fontWeight: 700, fontSize: '1.25rem' }}>
          List
        </Typography>
      </Toolbar>

      <Divider />

      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <List
          component="nav"
          aria-label="Main"
          subheader={<ListSubheader disableSticky>Menu</ListSubheader>}
          sx={{ px: 1 }}
        >
          {/* Above Lists, because it is what a member opens to (R44). No group
              of its own: the agenda is one screen. */}
          <ListItemButton
            component={NavLink}
            to="/dashboard"
            onClick={onNavigate}
            selected={pathname.startsWith('/dashboard')}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>
              <TodayRounded />
            </ListItemIcon>
            <ListItemText primary="Agenda" />
          </ListItemButton>

          <NavGroup
            label="Lists"
            to="/lists"
            icon={<ChecklistRounded />}
            open={open.lists}
            onToggle={() => setOpen((current) => ({ ...current, lists: !current.lists }))}
            createTo="/lists/new"
            createLabel="Create a list"
            emptyLabel="No lists yet"
            selected={pathname.startsWith('/lists')}
            onNavigate={onNavigate}
            entries={(sidebar?.lists ?? []).map((list) => ({
              id: list.id,
              name: list.name,
              icon: list.icon,
              pinned: list.pinned,
              to: `/lists/${list.id}`,
            }))}
            pathname={pathname}
          />

          {/* Collections (R58). Below Lists, because work is what a member
              opens the app for; a collection is what they come back to. No
              pins inside it: a collection carries no per-member state beyond
              how often it is opened (adr/0029). */}
          <NavGroup
            label="Collections"
            to="/collections"
            icon={<CollectionsBookmarkRounded />}
            open={open.collections}
            onToggle={() =>
              setOpen((current) => ({ ...current, collections: !current.collections }))
            }
            createTo="/collections/new"
            createLabel="Create a collection"
            emptyLabel="No collections yet"
            selected={pathname.startsWith('/collections')}
            onNavigate={onNavigate}
            entries={(collections?.collections ?? []).map((collection) => ({
              id: collection.id,
              name: collection.name,
              icon: collection.icon,
              pinned: false,
              to: `/collections/${collection.id}`,
            }))}
            pathname={pathname}
          />

          {user.role === 'ADMIN' && (
            <ListItemButton
              component={NavLink}
              to="/admin"
              onClick={onNavigate}
              selected={pathname.startsWith('/admin')}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>
                <GroupRounded />
              </ListItemIcon>
              <ListItemText primary="Users" />
            </ListItemButton>
          )}
        </List>
      </Box>

      <Divider />

      <List sx={{ px: 1 }}>
        <ListItemButton
          component={NavLink}
          to="/profile"
          onClick={onNavigate}
          selected={pathname.startsWith('/profile')}
        >
          <ListItemIcon sx={{ minWidth: 40 }} aria-hidden>
            <MemberAvatar
              displayName={user.displayName}
              avatarImageId={user.avatarImageId}
              size={28}
            />
          </ListItemIcon>
          <ListItemText primary={user.displayName} secondary="Your profile" />
        </ListItemButton>

        <ListItemButton onClick={onSignOut}>
          <ListItemIcon sx={{ minWidth: 40 }}>
            <LogoutRounded />
          </ListItemIcon>
          <ListItemText primary="Sign out" />
        </ListItemButton>
      </List>
    </Box>
  )
}

interface NavEntry {
  id: string
  name: string
  icon: ListIconToken
  pinned: boolean
  to: string
}

/**
 * One menu entry that is both a destination and a group (0012 R99).
 *
 * <p>The chevron and the create button are siblings of the `ListItemButton`
 * rather than children of it: the label still navigates to the index, and a
 * button inside a button is the invalid nesting adr/0039 already had to undo
 * once. `Stack` with the row's own padding keeps them on one line without a
 * second layout mechanism.
 */
function NavGroup({
  label,
  to,
  icon,
  open,
  onToggle,
  createTo,
  createLabel,
  emptyLabel,
  selected,
  onNavigate,
  entries,
  pathname,
}: {
  label: string
  to: string
  icon: React.ReactNode
  open: boolean
  onToggle: () => void
  createTo: string
  createLabel: string
  emptyLabel: string
  selected: boolean
  onNavigate?: () => void
  entries: NavEntry[]
  pathname: string
}) {
  return (
    <>
      <Stack direction="row" sx={{ alignItems: 'center' }}>
        <ListItemButton
          component={NavLink}
          to={to}
          onClick={onNavigate}
          selected={selected}
          sx={{ flex: 1, minWidth: 0 }}
        >
          <ListItemIcon sx={{ minWidth: 40 }}>{icon}</ListItemIcon>
          <ListItemText primary={label} />
        </ListItemButton>

        <IconButton
          component={NavLink}
          to={createTo}
          onClick={onNavigate}
          aria-label={createLabel}
          size="small"
        >
          <AddRounded fontSize="small" />
        </IconButton>

        <IconButton
          onClick={onToggle}
          aria-label={open ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
          aria-expanded={open}
          size="small"
        >
          {open ? <ExpandLessRounded fontSize="small" /> : <ExpandMoreRounded fontSize="small" />}
        </IconButton>
      </Stack>

      <Collapse in={open} unmountOnExit>
        <List dense disablePadding sx={{ pl: 1 }}>
          {entries.length === 0 && (
            // R103: told there are none, rather than shown nothing and left to
            // wonder whether the request failed.
            <ListItemText
              primary={emptyLabel}
              sx={{ px: 2, py: 1 }}
              slotProps={{ primary: { variant: 'body2', color: 'text.secondary' } }}
            />
          )}

          {entries.map((entry) => (
            <ListItemButton
              key={entry.id}
              component={NavLink}
              to={entry.to}
              onClick={onNavigate}
              selected={pathname === entry.to}
            >
              <ListItemIcon sx={{ minWidth: 36 }}>
                <ListIcon token={entry.icon} fontSize="small" />
              </ListItemIcon>
              <ListItemText
                primary={entry.name}
                slotProps={{ primary: { noWrap: true } }}
              />
              {/* The pin is why this row is first (R101), so it says so. */}
              {entry.pinned && (
                <PushPinRounded
                  fontSize="small"
                  aria-label="Pinned"
                  sx={{ color: 'text.secondary', fontSize: '1rem' }}
                />
              )}
            </ListItemButton>
          ))}
        </List>
      </Collapse>
    </>
  )
}
