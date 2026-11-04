import { useState } from 'react'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import MenuRounded from '@mui/icons-material/MenuRounded'
import { Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser, useLogout } from '../auth/useCurrentUser'
import { SIDEBAR_WIDTH } from '../theme/theme'
import { SideNav } from './SideNav'

/**
 * The signed-in shell: navigation mounted once, around whichever screen is
 * routed into it. Rendered inside ProtectedRoute, so a user is always present
 * by the time this draws.
 *
 * This component owns the only piece of layout state in the application, and
 * calls useMediaQuery in the only place it is called. A temporary drawer opens
 * and closes, which is behaviour rather than placement, so the CSS-only rule
 * design/0003 set cannot express it - see docs/adr/0015 for why the rule was
 * narrowed here rather than worked around.
 */
export function AppShell() {
  const { data: user } = useCurrentUser()
  const logoutMutation = useLogout()
  const theme = useTheme()
  const wide = useMediaQuery(theme.breakpoints.up('sm'))
  const { pathname } = useLocation()
  // Which screen the drawer was opened over, rather than a bare boolean. Any
  // navigation changes the pathname and so closes the drawer by construction -
  // the back button included - so the overlay can never be left covering the
  // screen it moved to, and there is no effect correcting it after the fact.
  const [openedOver, setOpenedOver] = useState<string | null>(null)
  const mobileNavOpen = openedOver === pathname

  if (!user) {
    return null
  }

  const nav = (
    <SideNav
      user={user}
      onNavigate={wide ? undefined : () => setOpenedOver(null)}
      onSignOut={() => logoutMutation.mutate()}
    />
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100dvh' }}>
      {!wide && (
        <AppBar position="fixed">
          <Toolbar>
            <IconButton
              edge="start"
              color="inherit"
              aria-label="Open navigation"
              onClick={() => setOpenedOver(pathname)}
              sx={{ mr: 1 }}
            >
              <MenuRounded />
            </IconButton>
            <Typography component="span" sx={{ fontWeight: 700 }}>
              List
            </Typography>
          </Toolbar>
        </AppBar>
      )}

      <Drawer
        variant={wide ? 'permanent' : 'temporary'}
        open={wide || mobileNavOpen}
        onClose={() => setOpenedOver(null)}
        ModalProps={{ keepMounted: true }}
        sx={{
          width: wide ? SIDEBAR_WIDTH : undefined,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: SIDEBAR_WIDTH,
            boxSizing: 'border-box',
            pb: 'env(safe-area-inset-bottom)',
          },
        }}
      >
        {nav}
      </Drawer>

      <Box
        component="main"
        sx={{
          flex: 1,
          minWidth: 0,
          px: { xs: 2, sm: 3 },
          // Clears the fixed app bar, which only exists below the breakpoint.
          // Deliberately not written alongside `py`: sx applies system props in
          // its own order rather than the object's, so a `py` here would win
          // and drop the page heading underneath the app bar.
          pt: wide ? 3 : 'calc(3.5rem + 1rem)',
          pb: 'calc(1.5rem + env(safe-area-inset-bottom))',
        }}
      >
        <Box sx={{ maxWidth: '64rem', mx: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}
