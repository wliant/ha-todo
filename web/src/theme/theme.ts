import { createTheme } from '@mui/material/styles'

/**
 * The single source of truth for colour, radius and type.
 *
 * Seeded with the palette the hand-written CSS tokens carried, so adopting
 * Material Design is a restyle and not also a rebrand - the generated icons and
 * the manifest's theme_color stay correct. See docs/adr/0014.
 */

/** Matches the BRAND constant in scripts/generate-icons.mjs and the manifest. */
const BRAND_LIGHT = '#1f5c3f'
const BRAND_DARK = '#2f8f63'

export const theme = createTheme({
  // Emits --mui-palette-* for both schemes, which is what lets the hand-written
  // token block be deleted rather than mapped.
  cssVariables: { colorSchemeSelector: 'data-color-scheme' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: BRAND_LIGHT, contrastText: '#ffffff' },
        error: { main: '#a32020' },
        background: { default: '#f6f7f5', paper: '#ffffff' },
        text: { primary: '#1a1d1b', secondary: '#5d655f' },
        divider: '#d9ddd8',
      },
    },
    dark: {
      palette: {
        primary: { main: BRAND_DARK, contrastText: '#06130d' },
        error: { main: '#ff8a80' },
        background: { default: '#121513', paper: '#1b1f1d' },
        text: { primary: '#e9ece9', secondary: '#a3aca6' },
        divider: '#333a36',
      },
    },
  },
  breakpoints: {
    // 768px is 48rem - the project's one historical breakpoint. Without this
    // line MUI's own 600px default silently becomes the breakpoint and every
    // layout decision shifts by 168 pixels.
    values: { xs: 0, sm: 768, md: 1024, lg: 1280, xl: 1536 },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: ['system-ui', '-apple-system', "'Segoe UI'", 'sans-serif'].join(','),
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      // 44px is a thumb target; the old stylesheet set it on every button and
      // it is worth keeping.
      styleOverrides: { root: { minHeight: '2.75rem', textTransform: 'none' } },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: ({ theme }) => ({
          borderRadius: 8,
          // The selected row carries the brand, as --brand-soft did. MUI's
          // default is a neutral grey overlay, which on the sidebar reads as
          // "disabled" rather than "where you are".
          '&.Mui-selected': {
            backgroundColor: `color-mix(in srgb, ${theme.vars.palette.primary.main} 14%, transparent)`,
            '&:hover': {
              backgroundColor: `color-mix(in srgb, ${theme.vars.palette.primary.main} 22%, transparent)`,
            },
          },
        }),
      },
    },
    MuiAppBar: { defaultProps: { elevation: 0, color: 'primary' } },
  },
})

/** The width at which the sidebar stops overlaying and becomes permanent. */
export const SIDEBAR_WIDTH = '16rem'
