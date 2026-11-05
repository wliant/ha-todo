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
import * as dataListsApi from '../api/dataLists'
import * as dataEntriesApi from '../api/dataEntries'
import type { CollectionSummary } from '../api/dataLists'
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

function collection(id: string, name: string): CollectionSummary {
  return {
    id,
    name,
    description: null,
    icon: 'GROCERIES',
    owned: true,
    memberCount: 1,
    entryCount: 0,
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
            <Route path="/collections/:listId" element={<p>the collection</p>} />
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
    // The picker now asks for both kinds (R59). Mocked here so every existing
    // case keeps exercising one request per feature rather than a failing one.
    vi.spyOn(dataListsApi, 'fetchCollections').mockResolvedValue([
      collection('id-recipes', 'Recipes'),
    ])
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

  it('offers collections alongside lists, in their own group', async () => {
    renderShare('?url=https://example.com')

    expect(await screen.findByText('Lists')).toBeVisible()
    expect(screen.getByText('Collections')).toBeVisible()
    expect(screen.getByRole('button', { name: /Recipes/ })).toBeVisible()
  })

  it('makes a link into an entry when a collection is chosen', async () => {
    const create = vi.spyOn(dataEntriesApi, 'createEntry').mockResolvedValue({} as never)
    renderShare('?title=Nonna%20carbonara&url=https://example.com/c')

    await userEvent.click(await screen.findByRole('button', { name: /Recipes/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Add it' }))

    // The title becomes the name, and the link lives in the detail as an
    // anchor the sanitizer will keep.
    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-recipes',
        expect.objectContaining({
          name: 'Nonna carbonara',
          detailHtml: '<p><a href="https://example.com/c">https://example.com/c</a></p>',
        }),
      ),
    )
  })

  it('still preselects the most recent list rather than a collection', async () => {
    // Collections have no recency (adr/0029), so the preselection stays
    // todo-only. This is the documented gap, asserted so it stays deliberate.
    const createItem = vi.spyOn(itemsApi, 'createItem').mockResolvedValue({} as never)
    const createEntry = vi.spyOn(dataEntriesApi, 'createEntry').mockResolvedValue({} as never)
    renderShare('?url=https://example.com')

    await userEvent.click(await screen.findByRole('button', { name: 'Add it' }))

    await waitFor(() => expect(createItem).toHaveBeenCalled())
    expect(createEntry).not.toHaveBeenCalled()
  })

  it('says so when the share carried nothing usable', async () => {
    renderShare('')
    expect(await screen.findByText(/Nothing came through/)).toBeVisible()
  })

  it('sends a member with no lists to make one', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], recent: [] })
    vi.spyOn(dataListsApi, 'fetchCollections').mockResolvedValue([])
    renderShare('?url=https://example.com')

    expect(await screen.findByRole('link', { name: 'Make a list first' })).toBeVisible()
  })
})
