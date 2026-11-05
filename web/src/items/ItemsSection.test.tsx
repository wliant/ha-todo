import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ItemsSection } from './ItemsSection'
import * as itemsApi from '../api/items'
import * as authApi from '../api/auth'
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
    // The selection bar assigns to whoever is signed in (R46).
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      avatarImageId: null,
    })
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

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    await waitFor(() =>
      expect(complete).toHaveBeenCalledWith('id-item', 3, true, undefined),
    )
  })

  it('moves the row at once rather than after the round trip', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    let settle = (): void => {}
    vi.spyOn(itemsApi, 'setItemCompleted').mockReturnValue(
      new Promise((done) => {
        settle = () => done(item({ completed: true }))
      }),
    )
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    // The completed row offers Reopen instead of Complete, so the swap is the
    // optimistic move - there is no checkbox to watch any more.
    expect(screen.getByRole('button', { name: 'Reopen Buy milk' })).toBeVisible()
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

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    expect(await screen.findByText('Ben completed this first.')).toBeVisible()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Reopen Buy milk' })).toBeVisible(),
    )
    expect(screen.getByText(/Ben — 50 points/)).toBeVisible()
  })

  it('puts the row back when the write simply failed', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    vi.spyOn(itemsApi, 'setItemCompleted').mockRejectedValue(new Error('offline'))
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Complete Buy milk' })).toBeVisible(),
    )
    expect(screen.queryByRole('button', { name: 'Reopen Buy milk' })).not.toBeInTheDocument()
  })

  it('asks only for the fields the list declared', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
    await userEvent.click(screen.getByRole('button', { name: 'More fields' }))

    expect(screen.getByRole('textbox', { name: 'Description' })).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Assignee' })).toBeVisible()
    // Never declared, so never rendered - not merely hidden behind the toggle.
    // By label, not by role: a datetime-local input has no `textbox` role, so
    // a role query here passes whether or not the input is there.
    expect(screen.queryByLabelText(/^Due/)).not.toBeInTheDocument()
  })

  it('starts the add form with only what the list insists on', async () => {
    // R39: most items are a line of text, and a form asking six questions for
    // "milk" is why somebody reaches for paper instead.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([
      description,
      field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false }),
      field({ id: 'id-due', kind: 'DUE_AT', required: true }),
    ])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))

    expect(screen.getByRole('textbox', { name: 'Description' })).toBeVisible()
    // Required, so it is asked for straight away.
    expect(screen.getByLabelText(/^Due/)).toBeVisible()
    // Optional, so it waits behind the toggle.
    expect(screen.queryByRole('combobox', { name: 'Assignee' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'More fields' }))

    expect(screen.getByRole('combobox', { name: 'Assignee' })).toBeVisible()
  })

  it('opens the whole form when editing, because the values are already there', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Buy milk' }))

    expect(screen.getByRole('combobox', { name: 'Assignee' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'More fields' })).not.toBeInTheDocument()
  })

  it('offers only the people who can see this list as assignees', async () => {
    // R24, in the form. The server checks it too.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
    await userEvent.click(screen.getByRole('button', { name: 'More fields' }))
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

  it('names the row checkbox for selecting, never for completing', async () => {
    // The checkbox used to complete the item. If its name still contained
    // "Complete", every one of the old queries would keep matching and the
    // suite would stay green while asserting the wrong control (adr/0027).
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    renderItems()

    // Present, not "visible": MUI renders the real input visually hidden
    // behind its own styling.
    expect(await screen.findByRole('checkbox', { name: 'Select Buy milk' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /^Complete/ })).not.toBeInTheDocument()
  })

  it('shows the detail and the completion note only once a row is expanded', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({
        completed: true,
        detailHtml: '<p>Semi-skimmed</p>',
        completionNoteHtml: '<p>Only had whole</p>',
        completedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
        completedAt: '2026-11-05T09:00:00Z',
      }),
    ])
    renderItems()

    expect(await screen.findByText('Buy milk')).toBeVisible()
    expect(screen.queryByText('Semi-skimmed')).not.toBeInTheDocument()
    expect(screen.queryByText('Only had whole')).not.toBeInTheDocument()

    // Exact: the row's other buttons all mention the description too.
    await userEvent.click(screen.getByRole('button', { name: 'Buy milk' }))

    expect(await screen.findByText('Semi-skimmed')).toBeVisible()
    expect(screen.getByText('Only had whole')).toBeVisible()
    // R36: who finished it and when, which the product stored all along and
    // never showed.
    expect(screen.getByText(/Finished .* by Ana/)).toBeVisible()
  })

  it('expanding one row collapses whichever was open', async () => {
    // R38, and the reason the open row is one id in the section rather than a
    // boolean in each row.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'First', detailHtml: '<p>First detail</p>' }),
      item({ id: 'id-b', description: 'Second', detailHtml: '<p>Second detail</p>' }),
    ])
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'First' }))
    expect(await screen.findByText('First detail')).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Second' }))

    expect(await screen.findByText('Second detail')).toBeVisible()
    await waitFor(() => expect(screen.queryByText('First detail')).not.toBeInTheDocument())
  })

  it('reaches the completion note by an ordinary click, with no hold at all', async () => {
    // The hold is an accelerator; this button is what makes the note reachable
    // without a pointer gesture (WCAG 2.1 SC 2.5.1, adr/0027).
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    const complete = vi
      .spyOn(itemsApi, 'setItemCompleted')
      .mockResolvedValue(item({ completed: true }))
    renderItems()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Complete Buy milk with a note' }),
    )

    // Opening the dialog must not complete anything on its own.
    expect(await screen.findByRole('dialog')).toBeVisible()
    expect(complete).not.toHaveBeenCalled()
  })

  it('takes the selected items for the member who asked', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'First' }),
      item({ id: 'id-b', description: 'Second', version: 7 }),
    ])
    const assign = vi.spyOn(itemsApi, 'assignItems').mockResolvedValue({
      assigned: [item({ id: 'id-a' }), item({ id: 'id-b', version: 7 })],
      conflicts: [],
    })
    renderItems()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select First' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Second' }))
    expect(screen.getByText('2 selected')).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Assign to me' }))

    await waitFor(() =>
      expect(assign).toHaveBeenCalledWith('id-list', 'id-ana', [
        { id: 'id-a', version: 3 },
        { id: 'id-b', version: 7 },
      ]),
    )
    // Everything landed, so nothing is left selected.
    await waitFor(() => expect(screen.queryByText(/selected/)).not.toBeInTheDocument())
  })

  it('keeps only the items somebody else had already changed selected', async () => {
    // The payoff of partial success: a second tap retries exactly the ones
    // that missed, carrying the versions the server just handed back.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'First' }),
      item({ id: 'id-b', description: 'Second' }),
    ])
    vi.spyOn(itemsApi, 'assignItems').mockResolvedValue({
      assigned: [item({ id: 'id-a', description: 'First' })],
      conflicts: [
        {
          id: 'id-b',
          error: 'Somebody gave this to Ben first.',
          item: item({ id: 'id-b', description: 'Second', version: 9 }),
        },
      ],
    })
    renderItems()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select First' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Second' }))
    await userEvent.click(screen.getByRole('button', { name: 'Assign to me' }))

    expect(await screen.findByText('Took 1. 1 had already changed.')).toBeVisible()
    await waitFor(() => expect(screen.getByText('1 selected')).toBeVisible())
    expect(screen.getByRole('checkbox', { name: 'Select Second' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Select First' })).not.toBeChecked()
  })

  it('marks work somebody took for themselves, by name and not only by colour', async () => {
    // R47, asserted through the accessible name so a colour-blind member, a
    // screen reader and this test all read the same fact.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({
        id: 'id-a',
        description: 'Claimed',
        assignee: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
        selfAssigned: true,
      }),
      item({
        id: 'id-b',
        description: 'Given',
        assignee: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
        selfAssigned: false,
      }),
    ])
    renderItems()

    expect(await screen.findByLabelText('Ben claimed this')).toBeVisible()
    expect(screen.getByLabelText('Assigned to Ben')).toBeVisible()
  })

  it('sends the note along with the completion, as one write', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    const complete = vi
      .spyOn(itemsApi, 'setItemCompleted')
      .mockResolvedValue(item({ completed: true }))
    renderItems()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Complete Buy milk with a note' }),
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Complete' }))

    // No note typed, so null rather than empty markup - "nothing" has one
    // representation, as the sanitizer insists.
    await waitFor(() =>
      expect(complete).toHaveBeenCalledWith('id-item', 3, true, null),
    )
  })

  it('reopens a completed item, which takes its points back', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({
        completed: true,
        pointsEarned: 50,
        completedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
      }),
    ])
    const reopen = vi.spyOn(itemsApi, 'setItemCompleted').mockResolvedValue(item())
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Reopen Buy milk' }))

    await waitFor(() =>
      expect(reopen).toHaveBeenCalledWith('id-item', 3, false, undefined),
    )
  })
})
