import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import CssBaseline from '@mui/material/CssBaseline'
import InitColorSchemeScript from '@mui/material/InitColorSchemeScript'
import { ThemeProvider } from '@mui/material/styles'
import { App } from './App'
import { ColorSchemeSync } from './theme/ColorSchemeSync'
import { theme } from './theme/theme'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Lists change when another household member edits them, so refetching
      // on focus is the desired default rather than an annoyance.
      refetchOnWindowFocus: true,
      retry: false,
    },
  },
})

// Registered for installability only - the worker caches nothing on purpose.
// See docs/adr/0007-installable-pwa-without-offline-caching.md.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js')
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* Paints the stored scheme on the first frame, before /api/me has
        resolved. Without it a dark-mode member sees a light flash on every
        reload, which reads as the app being broken. */}
    <InitColorSchemeScript attribute="data-color-scheme" />
    <ThemeProvider theme={theme} defaultMode="system">
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ColorSchemeSync />
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
