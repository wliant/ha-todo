import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ItemsSection } from './ItemsSection'
import * as itemsApi from '../api/items'
import type { Item } from '../api/items'
import type { ListDetail, ListField } from '../api/lists'
import { theme } from '../theme/theme'

const list: ListDetail = {
  id: 'id-list',
  name: 'Chores',
  description: null,
  icon: 'CHECKLIST',
  owned: true,
  pinned: false,
  version: 1,
  members: [
    { id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null },
    { id: 'id-ben', displayName: 'Ben', owner: false, avatarImageId: null },
  ],
  createdAt: '2026-11-05T00:00:00Z',
}

function field(overrides: Partial<ListField>): ListField {
  return {
    id: 'id-field',
    kind: 'DESCRIPTION',
    label: null,
    required: true,
    costMode: null,
    costUnitLabel: null,
    pointsPerUnit: null,
    choices: [],
    ...overrides,
  }
}

const description = field({ id: 'id-desc' })

function item(overrides: Partial<Item> = {}): Item {
  return {
    id: 'id-item',
    listId: 'id-list',
    description: 'Buy milk',
    detailHtml: null,
    assignee: null,
    selfAssigned: false,
    dueAt: null,
    completed: false,
    completedBy: null,
    completedAt: null,
    completionNoteHtml: null,
    reopenedAt: null,
    pointsEarned: null,
    costValues: [],
    version: 3,
    ...overrides,
  }
}

function renderItems(fields: ListField[] = [description]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <ItemsSection list={list} fields={fields} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

const conflict = {
  isAxiosError: true,
  response: {
    status: 409,
    data: {
      code: 'STALE_ITEM',
      error: 'Ben completed this first.',
      item: item({ completed: true, version: 4, pointsEarned: 50,
        completedBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null } }),
    },
  },
}

describe('ItemsSection', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(itemsApi, 'fetchPoints').mockResolvedValue([])
  })

  it('shows the list’s items', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    renderItems()

    expect(await screen.findByText('Buy milk')).toBeVisible()
  })

  it('ticks an item off, sending the version it was drawn from', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    const complete = vi.spyOn(itemsApi, 'setItemCompleted').mockResolvedValue(item({ completed: true }))
    renderItems()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Buy milk' }))

    await waitFor(() => expect(complete).toHaveBeenCalledWith('id-item', 3, true))
  })

  it('moves the checkbox at once rather than after the round trip', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    let settle = (): void => {}
    vi.spyOn(itemsApi, 'setItemCompleted').mockReturnValue(
      new Promise((done) => {
        settle = () => done(item({ completed: true }))
      }),
    )
    renderItems()

    const box = await screen.findByRole('checkbox', { name: 'Complete Buy milk' })
    await userEvent.click(box)

    expect(box).toBeChecked()
    settle()
  })

  it('tells the member who got there first, and shows what actually happened', async () => {
    // R30: the second writer is told, not silently overwritten - and the body
    // carries the truth so nothing has to be guessed or refetched to show it.
    const completedByBen = item({
      completed: true,
      version: 4,
      pointsEarned: 50,
      completedBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
    })
    // The refetch that follows returns the truth, as the server would.
    vi.spyOn(itemsApi, 'fetchItems')
      .mockResolvedValueOnce([item()])
      .mockResolvedValue([completedByBen])
    vi.spyOn(itemsApi, 'setItemCompleted').mockRejectedValue(conflict)
    renderItems()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Buy milk' }))

    expect(await screen.findByText('Ben completed this first.')).toBeVisible()
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Complete Buy milk' })).toBeChecked(),
    )
    expect(screen.getByText(/Ben — 50 points/)).toBeVisible()
  })

  it('puts the checkbox back when the write simply failed', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    vi.spyOn(itemsApi, 'setItemCompleted').mockRejectedValue(new Error('offline'))
    renderItems()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Buy milk' }))

    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Complete Buy milk' })).not.toBeChecked(),
    )
  })

  it('asks only for the fields the list declared', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))

    expect(screen.getByRole('textbox', { name: 'Description' })).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Assignee' })).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'Due' })).not.toBeInTheDocument()
  })

  it('offers only the people who can see this list as assignees', async () => {
    // R24, in the form. The server checks it too.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'Assignee' }))

    expect(await screen.findByRole('option', { name: 'Ana' })).toBeVisible()
    expect(screen.getByRole('option', { name: 'Ben' })).toBeVisible()
  })

  it('adds an item', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    const create = vi.spyOn(itemsApi, 'createItem').mockResolvedValue(item())
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Description' }), 'Water plants')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-list',
        expect.objectContaining({ description: 'Water plants' }),
      ),
    )
  })
})
