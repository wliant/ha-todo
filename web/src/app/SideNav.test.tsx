import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SideNav } from './SideNav'
import * as listsApi from '../api/lists'
import type { CurrentUser } from '../api/auth'
import type { ListSummary } from '../api/lists'
import { theme } from '../theme/theme'

const ana: CurrentUser = {
  id: 'id-ana',
  username: 'ana',
  displayName: 'Ana',
  role: 'MEMBER',
    avatarImageId: null,
}

function list(id: string, name: string, pinned = false): ListSummary {
  return {
    id,
    name,
    description: null,
    icon: 'CHECKLIST',
    owned: true,
    pinned,
    memberCount: 1,
  }
}

function renderNav(user: CurrentUser = ana) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter>
          <SideNav user={user} onSignOut={() => {}} />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('SideNav', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the member’s pinned lists', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({
      pinned: [list('id-g', 'Groceries', true)],
      recent: [],
    })
    renderNav()

    expect(await screen.findByText('Pinned')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Groceries' })).toHaveAttribute(
      'href',
      '/lists/id-g',
    )
  })

  it('leaves out the pinned section entirely when nothing is pinned', async () => {
    // An empty labelled section is noise.
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({
      pinned: [],
      recent: [list('id-c', 'Chores')],
    })
    renderNav()

    expect(await screen.findByText('Recent')).toBeVisible()
    expect(screen.queryByText('Pinned')).not.toBeInTheDocument()
  })

  it('keeps the create button reachable even with no lists at all', async () => {
    // Recent always shows, because it carries the create button and a member
    // with nothing is exactly who needs it.
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], recent: [] })
    renderNav()

    expect(await screen.findByRole('link', { name: 'Create a list' })).toHaveAttribute(
      'href',
      '/lists/new',
    )
  })

  it('offers administration to the admin only', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], recent: [] })
    renderNav({ ...ana, role: 'ADMIN' })

    expect(await screen.findByRole('link', { name: 'Users' })).toBeVisible()
  })

  it('does not offer administration to a member', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], recent: [] })
    renderNav()
    await screen.findByText('Recent')

    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })
})
