import Box from '@mui/material/Box'
import Collapse from '@mui/material/Collapse'
import Divider from '@mui/material/Divider'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import ChecklistRounded from '@mui/icons-material/ChecklistRounded'
import CollectionsBookmarkRounded from '@mui/icons-material/CollectionsBookmarkRounded'
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import TodayRounded from '@mui/icons-material/TodayRounded'
import GroupRounded from '@mui/icons-material/GroupRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import IconButton from '@mui/material/IconButton'
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import type { CurrentUser } from '../api/auth'
import type { ListIcon as ListIconToken, ListSummary } from '../api/lists'
import type { CollectionSummary } from '../api/dataLists'
import { ListIcon } from '../lists/listIcons'
import { useSidebar } from '../lists/queries'
import { useCollectionSidebar } from '../data/queries'
import { MemberAvatar } from './MemberAvatar'

/**
 * The one navigation surface (R25), now reaching every list and collection the
 * member can see (0012 R98-R103).
 *
 * <p>Three sections. **Pinned** is first and holds both kinds together - a
 * member pins the handful of things they are actually working from, and which
 * of them is a list and which a collection is not how they think about it
 * (adr/0043). It is uncapped and scrolls, because a pin is an explicit
 * instruction and silently dropping the twelfth is the sidebar deciding it
 * knows better.
 *
 * <p>Below it, **Lists** and **Collections** are expandable groups holding
 * everything *not* pinned, ordered by the member's own use over the last month
 * (adr/0041) and capped at their preference. The old Recent section showed only
 * lists with a recorded interaction, so a list just created - and every list
 * somebody else shared - was absent until it had been opened once, which is the
 * opposite of what a shortcut is for.
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
  const listsQuery = useSidebar()
  const collectionsQuery = useCollectionSidebar()
  const sidebar = listsQuery.data
  const collections = collectionsQuery.data

  // Both open by default: these groups replace the Pinned and Recent sections
  // wholesale, so a sidebar that starts collapsed offers strictly less than the
  // one it replaced. Not persisted - SideNav mounts once inside AppShell and
  // the mobile drawer is keepMounted, so a collapse survives every navigation
  // within the session, which is the whole span anybody would notice.
  const [open, setOpen] = useState({ lists: true, collections: true })

  // Both kinds in one section, merged by name. Deliberately not interleaved by
  // when each was pinned: the two halves arrive from two requests (adr/0042)
  // with no shared clock to order them by, and exposing a pin timestamp on a
  // DTO this widely used to answer a question nobody asked is the worse trade.
  // The icon says which kind a row is.
  const pinnedEntries: NavEntry[] = [
    ...(sidebar?.pinned ?? []).map(asListEntry),
    ...(collections?.pinned ?? []).map(asCollectionEntry),
  ].sort((left, right) => left.name.localeCompare(right.name))

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2 }}>
        <Typography component="span" sx={{ fontWeight: 700, fontSize: '1.25rem' }}>
          List
        </Typography>
      </Toolbar>

      <Divider />

      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <PinnedSection
          entries={pinnedEntries}
          // Either half is enough. If one request fails the other's pins are
          // still worth drawing, and the group below reports its own failure -
          // requiring both was the same conflation NavGroup had.
          ready={listsQuery.isSuccess || collectionsQuery.isSuccess}
          pathname={pathname}
          onNavigate={onNavigate}
        />

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
            status={statusOf(listsQuery)}
            selected={pathname.startsWith('/lists')}
            onNavigate={onNavigate}
            entries={(sidebar?.lists ?? []).map(asListEntry)}
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
            status={statusOf(collectionsQuery)}
            selected={pathname.startsWith('/collections')}
            onNavigate={onNavigate}
            entries={(collections?.collections ?? []).map(asCollectionEntry)}
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

/**
 * Which of three things a group has to say, kept apart deliberately.
 *
 * <p>Collapsing them is the defect this type exists to prevent: a group that
 * reads its rows out of `data ?? []` is empty while the request is in flight
 * and empty when the request *failed*, and in both cases announced "No lists
 * yet" - a confident lie about somebody's own data, shown for exactly as long
 * as their connection is slow, or forever.
 */
type GroupStatus = 'pending' | 'error' | 'ready'

function statusOf(query: { isPending: boolean; isError: boolean }): GroupStatus {
  if (query.isPending) {
    return 'pending'
  }
  return query.isError ? 'error' : 'ready'
}

interface NavEntry {
  id: string
  name: string
  icon: ListIconToken
  to: string
}

function asListEntry(list: ListSummary): NavEntry {
  return { id: list.id, name: list.name, icon: list.icon, to: `/lists/${list.id}` }
}

function asCollectionEntry(collection: CollectionSummary): NavEntry {
  return {
    id: collection.id,
    name: collection.name,
    icon: collection.icon,
    to: `/collections/${collection.id}`,
  }
}

/**
 * How tall the pinned section is allowed to get before it scrolls (0012 R106).
 *
 * <p>A height rather than a row count, because what matters is how much of the
 * sidebar the section is allowed to eat - the menu underneath has to stay
 * visible without scrolling the whole drawer. Roughly eight rows.
 */
const PINNED_MAX_HEIGHT = '15rem'

/**
 * The things the member pinned, lists and collections together (0012
 * R104-R106).
 *
 * <p>Scrolls inside itself rather than capping, and renders nothing at all
 * when empty - unlike the two groups below, which say "no lists yet" because
 * they are permanent fixtures with a create button. An empty labelled Pinned
 * section is noise, and a member who has never pinned anything does not need
 * telling.
 */
function PinnedSection({
  entries,
  ready,
  pathname,
  onNavigate,
}: {
  entries: NavEntry[]
  ready: boolean
  pathname: string
  onNavigate?: () => void
}) {
  // `ready` keeps the section from flashing in and out on first paint: with no
  // data yet, `entries` is empty for the same reason it is empty for a member
  // with no pins, and the two must not look the same.
  if (!ready || entries.length === 0) {
    return null
  }
  return (
    <List
      dense
      aria-label="Pinned"
      subheader={<ListSubheader disableSticky>Pinned</ListSubheader>}
      sx={{ px: 1, maxHeight: PINNED_MAX_HEIGHT, overflowY: 'auto' }}
    >
      {entries.map((entry) => (
        <ListItemButton
          key={`${entry.to}`}
          component={NavLink}
          to={entry.to}
          onClick={onNavigate}
          selected={pathname === entry.to}
        >
          <ListItemIcon sx={{ minWidth: 36 }}>
            <ListIcon token={entry.icon} fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={entry.name} slotProps={{ primary: { noWrap: true } }} />
        </ListItemButton>
      ))}
    </List>
  )
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
  status,
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
  status: GroupStatus
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
          {status === 'pending' && (
            // Not the empty label. "No lists yet" while the request is still
            // in flight is a confident lie about somebody's own data, and it
            // is what a member saw for as long as their connection was slow.
            <Box sx={{ px: 2, py: 1 }}>
              <Skeleton width="70%" />
              <Skeleton width="55%" />
            </Box>
          )}

          {status === 'error' && (
            <ListItemText
              primary={`Couldn't load your ${label.toLowerCase()}.`}
              sx={{ px: 2, py: 1 }}
              slotProps={{ primary: { variant: 'body2', color: 'error.main' } }}
            />
          )}

          {status === 'ready' && entries.length === 0 && (
            // R103: told there are none - and only once we actually know that,
            // rather than being shown nothing and left to wonder whether the
            // request failed.
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
            </ListItemButton>
          ))}
        </List>
      </Collapse>
    </>
  )
}
