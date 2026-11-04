import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SharePage } from './SharePage'
import * as authApi from '../api/auth'
import * as itemsApi from '../api/items'
import * as listsApi from '../api/lists'
import type { ListSummary } from '../api/lists'
import { theme } from '../theme/theme'

function list(id: string, name: string): ListSummary {
  return {
    id,
    name,
    description: null,
    icon: 'CHECKLIST',
    owned: true,
    pinned: false,
    memberCount: 1,
  }
}

function renderShare(query: string) {
  vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role: 'MEMBER',
    avatarImageId: null,
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={[`/share${query}`]}>
          <Routes>
            <Route path="/share" element={<SharePage />} />
            <Route path="/lists/:listId" element={<p>the list</p>} />
            <Route path="/lists" element={<p>all lists</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('SharePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({
      pinned: [],
      recent: [list('id-recent', 'Groceries'), list('id-older', 'Chores')],
    })
  })

  it('shows what arrived, recovering the url Chrome put in the text', async () => {
    renderShare('?text=Look%20at%20this%20https://example.com/pie')

    expect(await screen.findByText('https://example.com/pie')).toBeVisible()
    expect(screen.getByText('Look at this')).toBeVisible()
  })

  it('adds it to the most recently touched list without being asked', async () => {
    // One tap in the common case - the member was just looking at it.
    const create = vi.spyOn(itemsApi, 'createItem').mockResolvedValue({} as never)
    renderShare('?title=A%20recipe&url=https://example.com')

    await userEvent.click(await screen.findByRole('button', { name: 'Add it' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-recent',
        expect.objectContaining({ description: 'A recipe - https://example.com' }),
      ),
    )
  })

  it('lets the member put it somewhere else', async () => {
    const create = vi.spyOn(itemsApi, 'createItem').mockResolvedValue({} as never)
    renderShare('?url=https://example.com')

    await userEvent.click(await screen.findByRole('button', { name: 'Chores' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add it' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith('id-older', expect.anything()))
  })

  it('says so when the share carried nothing usable', async () => {
    renderShare('')
    expect(await screen.findByText(/Nothing came through/)).toBeVisible()
  })

  it('sends a member with no lists to make one', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], recent: [] })
    renderShare('?url=https://example.com')

    expect(await screen.findByRole('link', { name: 'Make a list first' })).toBeVisible()
  })
})
