import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import ChecklistRounded from '@mui/icons-material/ChecklistRounded'
import GroupRounded from '@mui/icons-material/GroupRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import { NavLink, useLocation } from 'react-router-dom'
import type { CurrentUser } from '../api/auth'
import { initials } from './initials'

/**
 * The one navigation surface (R25). Three sections: the member's pinned lists,
 * their recent lists with a way to create one, and the menu.
 *
 * Pinned and recent arrive with the list domain; the menu and the profile
 * footer are real now. An empty labelled section is noise, so each list section
 * is omitted entirely until it has something in it.
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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2 }}>
        <Typography component="span" sx={{ fontWeight: 700, fontSize: '1.25rem' }}>
          List
        </Typography>
      </Toolbar>

      <Divider />

      <List
        component="nav"
        aria-label="Main"
        subheader={<ListSubheader disableSticky>Menu</ListSubheader>}
        sx={{ px: 1 }}
      >
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

      {/* Pushes the profile to the bottom without needing a height calculation. */}
      <Box sx={{ flex: 1 }} />

      <Divider />

      <List sx={{ px: 1 }}>
        <ListItemButton
          component={NavLink}
          to="/profile"
          onClick={onNavigate}
          selected={pathname.startsWith('/profile')}
        >
          <ListItemIcon sx={{ minWidth: 40 }} aria-hidden>
            <Avatar sx={{ width: 28, height: 28, fontSize: '0.8rem' }}>
              {initials(user.displayName)}
            </Avatar>
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
