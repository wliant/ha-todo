import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CollectionDetailPage } from './CollectionDetailPage'
import * as collectionsApi from '../api/dataLists'
import * as entriesApi from '../api/dataEntries'
import type { EntryHit } from '../api/dataEntries'
import { theme } from '../theme/theme'

function hit(id: string, name: string, tags: string[] = []): EntryHit {
  return {
    id,
    dataListId: 'id-recipes',
    listName: 'Recipes',
    listIcon: 'GROCERIES',
    name,
    description: null,
    tags,
    version: 0,
    updatedAt: '2026-11-05T10:00:00Z',
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={['/collections/id-recipes']}>
          <Routes>
            <Route path="/collections/:listId" element={<CollectionDetailPage />} />
            <Route path="/collections" element={<p>all collections</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('CollectionDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(collectionsApi, 'fetchCollection').mockResolvedValue({
      id: 'id-recipes',
      name: 'Recipes',
      description: 'What worked',
      icon: 'GROCERIES',
      owned: true,
      pinned: false,
      version: 0,
      members: [],
      createdAt: '2026-11-05T10:00:00Z',
    })
    vi.spyOn(collectionsApi, 'fetchTagSuggestions').mockResolvedValue([
      { tag: 'italian', count: 2 },
      { tag: 'quick', count: 1 },
    ])
    vi.spyOn(entriesApi, 'fetchEntries').mockResolvedValue([
      hit('id-a', 'Tiramisu', ['italian']),
      hit('id-b', 'Pavlova'),
    ])
  })

  it('shows the collection and its entries', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Recipes' })).toBeVisible()
    expect(screen.getByText('What worked')).toBeVisible()
    expect(await screen.findByText('Tiramisu')).toBeVisible()
  })

  it('searches within the collection as the member types', async () => {
    renderPage()
    await screen.findByText('Tiramisu')

    await userEvent.type(screen.getByLabelText(/Search this collection/), 'tira')

    await waitFor(() =>
      expect(entriesApi.fetchEntries).toHaveBeenCalledWith('id-recipes', {
        q: 'tira',
        tags: [],
      }),
    )
  })

  it('narrows by tag, and several tags mean all of them', async () => {
    renderPage()
    await screen.findByText('Tiramisu')

    await userEvent.click(screen.getByRole('button', { name: 'italian (2)' }))
    await userEvent.click(screen.getByRole('button', { name: 'quick (1)' }))

    // R56: a filter a member adds to gives them less to read, not more.
    await waitFor(() =>
      expect(entriesApi.fetchEntries).toHaveBeenCalledWith('id-recipes', {
        q: '',
        tags: ['italian', 'quick'],
      }),
    )
  })

  it('opens one entry at a time', async () => {
    vi.spyOn(entriesApi, 'fetchEntry').mockResolvedValue({
      id: 'id-a',
      dataListId: 'id-recipes',
      name: 'Tiramisu',
      description: null,
      detailHtml: '<p>savoiardi</p>',
      tags: [],
      version: 0,
      createdById: null,
      createdByName: null,
      createdAt: '2026-11-05T10:00:00Z',
      updatedAt: '2026-11-05T10:00:00Z',
    })
    renderPage()
    await screen.findByText('Tiramisu')

    await userEvent.click(screen.getByRole('button', { name: 'Open Tiramisu' }))
    expect(await screen.findByText('savoiardi')).toBeVisible()

    // Opening the second collapses the first by construction - R57, so a
    // phone never becomes a wall of pictures.
    await userEvent.click(screen.getByRole('button', { name: 'Open Pavlova' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Open Tiramisu' })).toHaveAttribute(
        'aria-expanded',
        'false',
      ),
    )
  })

  it('hides the settings link from somebody who was only given access', async () => {
    vi.spyOn(collectionsApi, 'fetchCollection').mockResolvedValue({
      id: 'id-recipes',
      name: 'Recipes',
      description: null,
      icon: 'GROCERIES',
      owned: false,
      pinned: false,
      version: 0,
      members: [],
      createdAt: '2026-11-05T10:00:00Z',
    })
    renderPage()
    await screen.findByText('Tiramisu')

    expect(screen.queryByRole('link', { name: 'Collection settings' })).not.toBeInTheDocument()
    // But they can still add an entry: anybody with access is a peer (R60).
    expect(screen.getByRole('button', { name: /New entry/ })).toBeVisible()
  })

  it('says so when the collection is gone', async () => {
    vi.spyOn(collectionsApi, 'fetchCollection').mockRejectedValue(new Error('nope'))
    renderPage()

    expect(await screen.findByText(/no longer exists/)).toBeVisible()
  })

  it('distinguishes an empty collection from an empty search', async () => {
    vi.spyOn(entriesApi, 'fetchEntries').mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('Nothing in here yet.')).toBeVisible()

    await userEvent.type(screen.getByLabelText(/Search this collection/), 'zzz')
    expect(await screen.findByText('Nothing matches that.')).toBeVisible()
  })
})
