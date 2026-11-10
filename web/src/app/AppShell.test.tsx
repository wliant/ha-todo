import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'
import * as client from '../api/auth'
import type { UserRole } from '../api/auth'

/**
 * The drawer is the one piece of layout state the design admits (adr/0015), so
 * both of its variants are worth asserting - the permanent one above the
 * breakpoint, and the overlay plus hamburger below it.
 */
function atWidth(wide: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      // The shell asks exactly one question: is this at least the breakpoint?
      matches: wide,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }),
  })
}

/**
 * The compiled CSS for one element's emotion class.
 *
 * Needed because the two assertions below are about safe-area insets, and
 * jsdom resolves `env(safe-area-inset-top)` to 0 - there is no notch in a test
 * runner - so getComputedStyle cannot tell a shell that reserves the inset
 * from one that does not. The generated rule can. Read through the element's
 * own class rather than a literal, so a restyle moves the assertion with it.
 */
function rulesFor(element: HTMLElement) {
  const generated = element.className
    .split(' ')
    .find((name) => name.startsWith('css-'))
  const sheets = Array.from(document.querySelectorAll('style'))
    .map((style) => style.textContent ?? '')
    .join('')
  return new RegExp(`\\.${generated}\\{([^}]*)\\}`).exec(sheets)?.[1] ?? ''
}

function renderShell(role: UserRole, { wide = true } = {}) {
  atWidth(wide)
  vi.spyOn(client, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role,
    timezone: 'Asia/Singapore',
    avatarImageId: null,
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/lists']}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/lists" element={<p>the lists screen</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AppShell', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('names the signed-in member and routes around them', async () => {
    renderShell('MEMBER')

    expect(await screen.findByText('Ana')).toBeVisible()
    expect(screen.getByText('the lists screen')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Lists' })).toBeVisible()
  })

  it('offers administration to the admin only', async () => {
    renderShell('ADMIN')
    expect(await screen.findByRole('link', { name: 'Users' })).toBeVisible()
  })

  it('does not offer administration to a member', async () => {
    renderShell('MEMBER')
    await screen.findByText('Ana')

    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })

  it('links to the signed-in member’s own profile', async () => {
    renderShell('MEMBER')
    expect(await screen.findByRole('link', { name: /Ana/ })).toHaveAttribute(
      'href',
      '/profile',
    )
  })

  it('signs out from the sidebar', async () => {
    const logout = vi.spyOn(client, 'logout').mockResolvedValue()
    renderShell('MEMBER')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(logout).toHaveBeenCalled()
  })

  it('keeps the sidebar on screen above the breakpoint, with no way to open it', async () => {
    renderShell('MEMBER', { wide: true })
    await screen.findByText('Ana')

    expect(screen.getByRole('link', { name: 'Lists' })).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Open navigation' }),
    ).not.toBeInTheDocument()
  })

  it('hides the sidebar behind a hamburger below the breakpoint', async () => {
    renderShell('MEMBER', { wide: false })
    await screen.findByText('the lists screen')

    // keepMounted leaves the drawer in the DOM, but a closed one is hidden from
    // the accessibility tree - so it is not merely invisible, it is unreachable.
    expect(screen.queryByRole('link', { name: 'Lists' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Open navigation' }))

    expect(screen.getByRole('link', { name: 'Lists' })).toBeVisible()
  })

  /*
   * iOS draws a standalone PWA under the status bar, and the system takes any
   * tap that lands there - so an app bar at top: 0 with no inset leaves the
   * hamburger, the only control on the bar, unpressable on a notched iPhone
   * while working perfectly on Android, which does not paint under its status
   * bar. These two assert the inset is reserved and then cleared, because
   * reserving it on the bar without clearing it in main hides the heading of
   * every screen behind the taller bar.
   */
  it('keeps the hamburger clear of an iPhone’s status bar', async () => {
    renderShell('MEMBER', { wide: false })
    await screen.findByText('the lists screen')

    const bar = screen.getByRole('banner')

    expect(rulesFor(bar)).toContain('padding-top:env(safe-area-inset-top)')
  })

  it('clears the inset the app bar reserves, as well as the bar', async () => {
    renderShell('MEMBER', { wide: false })

    const main = await screen.findByRole('main')

    expect(rulesFor(main)).toContain('env(safe-area-inset-top)')
  })

  it('closes the overlay when a destination is chosen, so it cannot cover the new screen', async () => {
    renderShell('MEMBER', { wide: false })
    await screen.findByText('the lists screen')
    await userEvent.click(screen.getByRole('button', { name: 'Open navigation' }))

    await userEvent.click(screen.getByRole('link', { name: 'Lists' }))

    // The drawer slides out rather than vanishing, so this is reached rather
    // than immediate.
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Lists' })).not.toBeInTheDocument(),
    )
  })
})
