import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AllListsPage } from './AllListsPage'
import * as authApi from '../api/auth'
import * as listsApi from '../api/lists'
import type { ListSummary } from '../api/lists'
import { theme } from '../theme/theme'

const groceries: ListSummary = {
  id: 'id-groceries',
  name: 'Groceries',
  description: 'What we need',
  icon: 'GROCERIES',
  owned: true,
  pinned: false,
  memberCount: 2,
}

function renderPage() {
  vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role: 'MEMBER',
    timezone: 'Asia/Singapore',
    avatarImageId: null,
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={['/lists']}>
          <AllListsPage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('AllListsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('keeps the heading the sign-in flow waits on', async () => {
    // e2e's ui_sign_in asserts this exact heading after every sign-in, so it is
    // a contract shared with the suite rather than a free choice.
    vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([])
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Lists' })).toBeVisible()
  })

  it('shows the lists a member can reach', async () => {
    vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([groceries])
    renderPage()

    expect(await screen.findByText('Groceries')).toBeVisible()
    expect(screen.getByText('2 people')).toBeVisible()
  })

  it('offers a way to make the first one when there are none', async () => {
    vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('No lists yet.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Create your first list' })).toBeVisible()
  })

  it('sends the typed search to the server rather than filtering what it already has', async () => {
    const fetch = vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([groceries])
    renderPage()
    await screen.findByText('Groceries')

    await userEvent.type(screen.getByRole('textbox', { name: 'Search lists' }), 'groc')

    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(expect.objectContaining({ q: 'groc' })),
    )
  })

  it('narrows to the lists someone else shared', async () => {
    const fetch = vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([])
    renderPage()
    await screen.findByText('No lists match that.').catch(() => undefined)

    await userEvent.click(await screen.findByRole('button', { name: 'Shared with me' }))

    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(expect.objectContaining({ scope: 'shared' })),
    )
  })

  it('narrows to pinned lists', async () => {
    const fetch = vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([])
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Pinned only' }))

    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(expect.objectContaining({ pinned: true })),
    )
  })
})
