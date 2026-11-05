import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import ChecklistRounded from '@mui/icons-material/ChecklistRounded'
import CollectionsBookmarkRounded from '@mui/icons-material/CollectionsBookmarkRounded'
import TodayRounded from '@mui/icons-material/TodayRounded'
import GroupRounded from '@mui/icons-material/GroupRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import IconButton from '@mui/material/IconButton'
import { NavLink, useLocation } from 'react-router-dom'
import type { CurrentUser } from '../api/auth'
import type { ListSummary } from '../api/lists'
import { ListIcon } from '../lists/listIcons'
import { useSidebar } from '../lists/queries'
import { MemberAvatar } from './MemberAvatar'

/**
 * The one navigation surface (R25). Three sections: the member's pinned lists
 * (R27), their recent lists with a way to create one (R28), and the menu.
 *
 * Both list sections come from one request, so the shell asks once rather than
 * three times and the two cannot disagree while one is in flight. Pinned is
 * omitted entirely until something is pinned, because an empty labelled section
 * is noise; Recent always shows, because it carries the create button and a
 * member with no lists is exactly who needs it.
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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2 }}>
        <Typography component="span" sx={{ fontWeight: 700, fontSize: '1.25rem' }}>
          List
        </Typography>
      </Toolbar>

      <Divider />

      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        {sidebar && sidebar.pinned.length > 0 && (
          <ListSection
            title="Pinned"
            lists={sidebar.pinned}
            pathname={pathname}
            onNavigate={onNavigate}
          />
        )}

        <ListSection
          title="Recent"
          lists={sidebar?.recent ?? []}
          pathname={pathname}
          onNavigate={onNavigate}
          action={
            <IconButton
              component={NavLink}
              to="/lists/new"
              onClick={onNavigate}
              aria-label="Create a list"
              size="small"
              edge="end"
            >
              <AddRounded fontSize="small" />
            </IconButton>
          }
        />
      </Box>

      <Divider />

      <List
        component="nav"
        aria-label="Main"
        subheader={<ListSubheader disableSticky>Menu</ListSubheader>}
        sx={{ px: 1 }}
      >
        {/* Above Lists, because it is what a member opens to (R44). */}
        <ListItemButton
          component={NavLink}
          to="/dashboard"
          onClick={onNavigate}
          selected={pathname.startsWith('/dashboard')}
        >
          <ListItemIcon sx={{ minWidth: 40 }}>
            <TodayRounded />
          </ListItemIcon>
          <ListItemText primary="My day" />
        </ListItemButton>

        <ListItemButton
          component={NavLink}
          to="/lists"
          onClick={onNavigate}
          selected={pathname.startsWith('/lists')}
        >
          <ListItemIcon sx={{ minWidth: 40 }}>
            <ChecklistRounded />
          </ListItemIcon>
          <ListItemText primary="Lists" />
        </ListItemButton>

        {/* Collections (R58). Below Lists, because work is what a member
            opens the app for; a collection is what they come back to. No
            Pinned or Recent section of its own: collections carry no
            per-member state (adr/0029). */}
        <ListItemButton
          component={NavLink}
          to="/collections"
          onClick={onNavigate}
          selected={pathname.startsWith('/collections')}
        >
          <ListItemIcon sx={{ minWidth: 40 }}>
            <CollectionsBookmarkRounded />
          </ListItemIcon>
          <ListItemText primary="Collections" />
        </ListItemButton>

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
 * One labelled group of lists. The create button lives in the Recent
 * subheader, where a member looking for somewhere to put something is already
 * looking.
 */
function ListSection({
  title,
  lists,
  pathname,
  onNavigate,
  action,
}: {
  title: string
  lists: ListSummary[]
  pathname: string
  onNavigate?: () => void
  action?: React.ReactNode
}) {
  return (
    <List
      dense
      sx={{ px: 1 }}
      subheader={
        <ListSubheader
          disableSticky
          sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
        >
          {title}
          {action}
        </ListSubheader>
      }
    >
      {lists.map((list) => (
        <ListItemButton
          key={list.id}
          component={NavLink}
          to={`/lists/${list.id}`}
          onClick={onNavigate}
          selected={pathname === `/lists/${list.id}`}
        >
          <ListItemIcon sx={{ minWidth: 36 }}>
            <ListIcon token={list.icon} fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={list.name} slotProps={{ primary: { noWrap: true } }} />
        </ListItemButton>
      ))}
    </List>
  )
}
