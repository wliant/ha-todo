import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EntryRow } from './EntryRow'
import * as entriesApi from '../api/dataEntries'
import type { EntryHit } from '../api/dataEntries'
import { theme } from '../theme/theme'

function hit(overrides: Partial<EntryHit> = {}): EntryHit {
  return {
    id: 'id-entry',
    dataListId: 'id-recipes',
    listName: 'Recipes',
    listIcon: 'GROCERIES',
    name: 'Tiramisu',
    description: 'Coffee and mascarpone',
    tags: ['italian'],
    version: 0,
    updatedAt: '2026-11-05T10:00:00Z',
    ...overrides,
  }
}

function renderRow(props: Partial<Parameters<typeof EntryRow>[0]> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter>
          <EntryRow
            entry={hit()}
            expanded={false}
            onToggle={vi.fn()}
            {...props}
          />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('EntryRow', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the name, description and tags while collapsed', () => {
    renderRow()

    expect(screen.getByText('Tiramisu')).toBeVisible()
    expect(screen.getByText('Coffee and mascarpone')).toBeVisible()
    expect(screen.getByText('italian')).toBeVisible()
  })

  it('does not fetch the detail until the row is opened', () => {
    // A collection of recipes would otherwise send every picture in it to
    // draw a list of names.
    const fetch = vi.spyOn(entriesApi, 'fetchEntry')
    renderRow()

    expect(fetch).not.toHaveBeenCalled()
  })

  it('fetches and renders the detail once expanded', async () => {
    vi.spyOn(entriesApi, 'fetchEntry').mockResolvedValue({
      id: 'id-entry',
      dataListId: 'id-recipes',
      name: 'Tiramisu',
      description: null,
      detailHtml: '<ul><li>savoiardi</li></ul>',
      tags: [],
      version: 0,
      createdById: null,
      createdByName: null,
      createdAt: '2026-11-05T10:00:00Z',
      updatedAt: '2026-11-05T10:00:00Z',
    })

    renderRow({ expanded: true })

    expect(await screen.findByText('savoiardi')).toBeVisible()
  })

  it('reports which collection a global hit came from', () => {
    // R54: a result from across every collection has to say where it is.
    renderRow({ showCollection: true })

    expect(screen.getByRole('link', { name: 'Recipes' })).toHaveAttribute(
      'href',
      '/collections/id-recipes',
    )
  })

  it('does not name a collection when the row is already inside one', () => {
    renderRow()

    expect(screen.queryByRole('link', { name: 'Recipes' })).not.toBeInTheDocument()
  })

  it('announces whether it is open, so the control is not a mystery', async () => {
    const onToggle = vi.fn()
    renderRow({ onToggle })

    const toggle = screen.getByRole('button', { name: 'Open Tiramisu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(toggle)
    expect(onToggle).toHaveBeenCalled()
  })

  it('offers edit and delete only when the parent supplies them', () => {
    const { unmount } = renderRow()
    expect(screen.queryByRole('button', { name: 'Edit Tiramisu' })).not.toBeInTheDocument()
    unmount()

    renderRow({ onEdit: vi.fn(), onDelete: vi.fn() })
    expect(screen.getByRole('button', { name: 'Edit Tiramisu' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Delete Tiramisu' })).toBeVisible()
  })
})
