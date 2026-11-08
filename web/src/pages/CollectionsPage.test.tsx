import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CollectionsPage } from './CollectionsPage'
import * as collectionsApi from '../api/dataLists'
import * as entriesApi from '../api/dataEntries'
import type { CollectionSummary } from '../api/dataLists'
import { theme } from '../theme/theme'

function collection(id: string, name: string, entryCount = 3): CollectionSummary {
  return {
    id,
    name,
    description: null,
    icon: 'GROCERIES',
    owned: true,
    pinned: false,
    memberCount: 1,
    entryCount,
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={['/collections']}>
          <CollectionsPage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('CollectionsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(collectionsApi, 'fetchCollections').mockResolvedValue([
      collection('id-recipes', 'Recipes'),
      collection('id-vocab', 'Vocabulary', 1),
    ])
  })

  it('lists the collections the member can see', async () => {
    renderPage()

    expect(await screen.findByText('Recipes')).toBeVisible()
    expect(screen.getByText('Vocabulary')).toBeVisible()
    expect(screen.getByText('3 entries')).toBeVisible()
    // Singular, because "1 entries" is the kind of thing a household notices.
    expect(screen.getByText('1 entry')).toBeVisible()
  })

  it('does not search until something is typed', async () => {
    // Opening the page would otherwise fetch every entry the member can see
    // in order to show nothing.
    const search = vi.spyOn(entriesApi, 'searchEntries')
    renderPage()

    await screen.findByText('Recipes')
    expect(search).not.toHaveBeenCalled()
  })

  it('searches across every collection and says where each hit came from', async () => {
    const search = vi.spyOn(entriesApi, 'searchEntries').mockResolvedValue([
      {
        id: 'id-entry',
        dataListId: 'id-recipes',
        listName: 'Recipes',
        listIcon: 'GROCERIES',
        name: 'Tiramisu',
        description: null,
        tags: [],
        version: 0,
        updatedAt: '2026-11-05T10:00:00Z',
      },
    ])
    renderPage()

    await userEvent.type(screen.getByLabelText(/Search everything/), 'tiramisu')

    await waitFor(() => expect(search).toHaveBeenCalledWith({ q: 'tiramisu' }))
    expect(await screen.findByText('Tiramisu')).toBeVisible()
    // R54: the hit names its collection.
    expect(screen.getByRole('link', { name: 'Recipes' })).toBeVisible()
  })

  it('replaces the cards with results, because searching is not browsing', async () => {
    vi.spyOn(entriesApi, 'searchEntries').mockResolvedValue([])
    renderPage()
    await screen.findByText('Vocabulary')

    await userEvent.type(screen.getByLabelText(/Search everything/), 'nothing')

    await waitFor(() => expect(screen.queryByText('Vocabulary')).not.toBeInTheDocument())
    expect(await screen.findByText(/Nothing matches/)).toBeVisible()
  })

  it('tells a member with no collections what one is for', async () => {
    vi.spyOn(collectionsApi, 'fetchCollections').mockResolvedValue([])
    renderPage()

    expect(await screen.findByText(/what you look up again/)).toBeVisible()
  })

  it('offers the same star a list card does', async () => {
    // Reversed from what this file used to assert. A collection had no
    // per-member state at all until adr/0043 gave it a pin; 0012 R104 is why.
    const pin = vi.spyOn(collectionsApi, 'setCollectionPinned').mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('Recipes')

    await userEvent.click(screen.getByRole('button', { name: 'Pin Recipes' }))

    expect(pin).toHaveBeenCalledWith('id-recipes', true)
  })
})
