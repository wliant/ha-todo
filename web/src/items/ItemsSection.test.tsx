import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
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
    state: 'OPEN',
    completedBy: null,
    completedAt: null,
    completionNoteHtml: null,
    lapsedBy: null,
    lapsedAt: null,
    reopenedAt: null,
    pointsEarned: null,
    costValues: [],
    version: 3,
    ...overrides,
  }
}

/**
 * @param at the address the list is opened at, which is where the filter lives
 *           (adr/0045). Several tests below start at `?status=open,done`
 *           because the default filter is To do and they are about a completed
 *           item - the server used to do that narrowing, so a mock could
 *           return a finished item and have it drawn regardless (adr/0044).
 */
function renderItems(fields: ListField[] = [description], at = '/lists/id-list') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <MemoryRouter initialEntries={[at]}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme} defaultMode="light">
          <ItemsSection list={list} fields={fields} />
        </ThemeProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

const BOTH = '/lists/id-list?status=open,done'

function assigneeField(): ListField {
  return field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })
}

const conflict = {
  isAxiosError: true,
  response: {
    status: 409,
    data: {
      code: 'STALE_ITEM',
      error: 'Ben completed this first.',
      item: item({ state: 'COMPLETED', version: 4, pointsEarned: 50,
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
      timezone: 'Asia/Singapore',
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
    const complete = vi.spyOn(itemsApi, 'setItemState').mockResolvedValue(item({ state: 'COMPLETED' }))
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    await waitFor(() =>
      expect(complete).toHaveBeenCalledWith('id-item', 3, 'COMPLETED', undefined),
    )
  })

  it('moves the row at once rather than after the round trip', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    let settle = (): void => {}
    vi.spyOn(itemsApi, 'setItemState').mockReturnValue(
      new Promise((done) => {
        settle = () => done(item({ state: 'COMPLETED' }))
      }),
    )
    renderItems([description], BOTH)

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    // Showing both statuses, so the row stays and swaps its control: Reopen
    // instead of Complete is the optimistic move.
    expect(screen.getByRole('button', { name: 'Reopen Buy milk' })).toBeVisible()
    settle()
  })

  it('says what happened when the filter takes the row away', async () => {
    // R119, and the reason it exists: with the default To do filter the
    // optimistic write makes the row vanish at once, and a row disappearing
    // cannot be the only feedback (adr/0039's rule, on a list).
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    // Never settles, so the refetch cannot do this assertion's work for it.
    vi.spyOn(itemsApi, 'setItemState').mockReturnValue(new Promise(() => {}))
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    expect(await screen.findByText('Finished “Buy milk”.')).toBeVisible()
    expect(screen.queryByText('Buy milk')).not.toBeInTheDocument()
  })

  it('lapses an item, sending the version it was drawn from', async () => {
    // 0015 R131, R137. Same shape as completing and deliberately so: one
    // endpoint, one state machine, the drawn version checked first.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    const lapse = vi
      .spyOn(itemsApi, 'setItemState')
      .mockResolvedValue(item({ state: 'LAPSED' }))
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Lapse Buy milk' }))

    await waitFor(() =>
      expect(lapse).toHaveBeenCalledWith('id-item', 3, 'LAPSED', undefined),
    )
  })

  it('says what happened when a lapse takes the row away', async () => {
    // R119 again, in its own words: the default filter is To do, so a lapsed
    // row leaves at once and the snackbar is the only thing that says why.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    // Never settles, so the refetch cannot do this assertion's work for it.
    vi.spyOn(itemsApi, 'setItemState').mockReturnValue(new Promise(() => {}))
    renderItems()

    await userEvent.click(await screen.findByRole('button', { name: 'Lapse Buy milk' }))

    expect(await screen.findByText('Lapsed “Buy milk”.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Buy milk' })).not.toBeInTheDocument()
  })

  it('shows a lapsed item under its own status, and offers to reopen it', async () => {
    // R135: lapsing is not a delete. The row keeps its disclosure and swaps
    // Complete for Reopen, exactly as a completed one does.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({
        state: 'LAPSED',
        lapsedAt: '2026-11-12T10:00:00Z',
        lapsedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
      }),
    ])
    const reopen = vi.spyOn(itemsApi, 'setItemState').mockResolvedValue(item())
    renderItems([description], '/lists/id-list?status=lapsed')

    expect(await screen.findByText('Lapsed')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Complete Buy milk' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Reopen Buy milk' }))

    await waitFor(() =>
      expect(reopen).toHaveBeenCalledWith('id-item', 3, 'OPEN', undefined),
    )
  })

  it('does not offer to lapse something already finished', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item({ state: 'COMPLETED' })])
    renderItems([description], BOTH)

    expect(await screen.findByRole('button', { name: 'Reopen Buy milk' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Lapse Buy milk' })).not.toBeInTheDocument()
  })

  it('tells the member who got there first, and shows what actually happened', async () => {
    // R30: the second writer is told, not silently overwritten - and the body
    // carries the truth so nothing has to be guessed or refetched to show it.
    const completedByBen = item({
      state: 'COMPLETED',
      version: 4,
      pointsEarned: 50,
      completedBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
    })
    // The refetch that follows returns the truth, as the server would.
    vi.spyOn(itemsApi, 'fetchItems')
      .mockResolvedValueOnce([item()])
      .mockResolvedValue([completedByBen])
    vi.spyOn(itemsApi, 'setItemState').mockRejectedValue(conflict)
    renderItems([description], BOTH)

    await userEvent.click(await screen.findByRole('button', { name: 'Complete Buy milk' }))

    expect(await screen.findByText('Ben completed this first.')).toBeVisible()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Reopen Buy milk' })).toBeVisible(),
    )
    expect(screen.getByText(/Ben — 50 points/)).toBeVisible()
  })

  it('puts the row back when the write simply failed', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    vi.spyOn(itemsApi, 'setItemState').mockRejectedValue(new Error('offline'))
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

    expect(screen.getByRole('textbox', { name: 'Description' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Ana' })).toBeVisible()
    // Never declared, so never rendered - not merely hidden behind the toggle.
    // Keyed off the due control's own chips rather than the "Due" legend: a
    // legend labels no form control, so a label query against it reports
    // nothing whether or not the control is there.
    expect(screen.queryByRole('button', { name: 'Today' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^Date/)).not.toBeInTheDocument()
  })

  it('asks up front for the two answers that cost a tap', async () => {
    // 0017 R148, R149, replacing "starts the add form with only what the list
    // insists on". R39's reasoning stands for the rest - a form asking six
    // questions for "milk" is why somebody reaches for paper - but `required`
    // was a proxy for the wrong property: these two cost a tap, and an item
    // without them is invisible to every planning surface (adr/0050).
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([
      description,
      field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false }),
      field({ id: 'id-due', kind: 'DUE_AT', required: false }),
      field({
        id: 'id-cost',
        kind: 'COST',
        label: 'Effort',
        required: false,
        costMode: 'FIXED',
        choices: [{ id: 'id-light', label: 'Light', points: 1 }],
      }),
    ])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))

    expect(screen.getByRole('textbox', { name: 'Description' })).toBeVisible()
    // Both optional, both on screen anyway.
    expect(screen.getByRole('button', { name: 'Ben' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tomorrow' })).toBeVisible()
    // The date's own controls are not: collapsed, it is the chips and nothing
    // else, which is what the disclosure still has to reveal (R151).
    expect(screen.queryByLabelText(/^Date/)).not.toBeInTheDocument()
    // A cost is a judgement rather than a tap, so it still waits.
    expect(screen.queryByRole('combobox', { name: /Effort/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'More fields' }))

    expect(screen.getByLabelText(/^Date/)).toBeVisible()
    expect(screen.getByRole('combobox', { name: /Effort/ })).toBeVisible()
  })

  it('opens the whole form when editing, because the values are already there', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Buy milk' }))

    expect(screen.getByRole('button', { name: 'Ana' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'More fields' })).not.toBeInTheDocument()
  })

  it('offers only the people who can see this list as assignees', async () => {
    // R24, in the form. The server checks it too.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))

    // One tap, no popup to open first (R150).
    expect(await screen.findByRole('button', { name: 'Ana' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Ben' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Nobody' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('assigns in one tap, from the collapsed form', async () => {
    const create = vi.spyOn(itemsApi, 'createItem').mockResolvedValue(item())
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems([description, field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })])

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Description' }), 'Bins out')
    await userEvent.click(screen.getByRole('button', { name: 'Ben' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-list',
        expect.objectContaining({ description: 'Bins out', assigneeId: 'id-ben' }),
      ),
    )
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
        state: 'COMPLETED',
        detailHtml: '<p>Semi-skimmed</p>',
        completionNoteHtml: '<p>Only had whole</p>',
        completedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
        completedAt: '2026-11-05T09:00:00Z',
      }),
    ])
    renderItems([description], BOTH)

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
      .spyOn(itemsApi, 'setItemState')
      .mockResolvedValue(item({ state: 'COMPLETED' }))
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
      .spyOn(itemsApi, 'setItemState')
      .mockResolvedValue(item({ state: 'COMPLETED' }))
    renderItems()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Complete Buy milk with a note' }),
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Complete' }))

    // No note typed, so null rather than empty markup - "nothing" has one
    // representation, as the sanitizer insists.
    await waitFor(() =>
      expect(complete).toHaveBeenCalledWith('id-item', 3, 'COMPLETED', null),
    )
  })

  it('reads the filter out of the address, and narrows the list by it', async () => {
    // R115: a reload, a navigation back, and a pasted link all show this.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'Ben’s', assignee: { id: 'id-ben', displayName: 'Ben', avatarImageId: null } }),
      item({ id: 'id-b', description: 'Nobody’s' }),
    ])
    renderItems([description, assigneeField()], '/lists/id-list?who=id-ben')

    expect(await screen.findByText('Ben’s')).toBeVisible()
    expect(screen.queryByText('Nobody’s')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 filter')
  })

  it('says a filtered list is filtered, not that it is empty', async () => {
    // R117. "Nothing to do here" is a confident statement about somebody's own
    // list, and here it is false - the same mistake the sidebar's groups made
    // by reading their rows out of `data ?? []`.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([item()])
    // Unassigned, so filtering to Ben's work matches nothing.
    renderItems([description, assigneeField()], '/lists/id-list?who=id-ben')

    expect(await screen.findByText('Nothing matches these filters.')).toBeVisible()
    expect(screen.queryByText('Nothing to do here.')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))

    // R118: clearing is one action, and it brings the list back - which is
    // also what proves the address was written rather than some local state.
    expect(await screen.findByText('Buy milk')).toBeVisible()
  })

  it('says an empty list is empty', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    renderItems()

    expect(await screen.findByText('Nothing to do here.')).toBeVisible()
  })

  it('shows both statuses at once, which the old toggle could not', async () => {
    // R108: "All" was a word for the empty filter, and the exclusive toggle
    // made the one combination anybody wanted unreachable (adr/0046).
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'Open one' }),
      item({ id: 'id-b', description: 'Finished one', state: 'COMPLETED' }),
    ])
    renderItems([description], BOTH)

    expect(await screen.findByText('Open one')).toBeVisible()
    expect(screen.getByText('Finished one')).toBeVisible()
  })

  it('cannot select an item the filter is hiding', async () => {
    // R121: "2 selected" must always be two rows the member can see.
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({ id: 'id-a', description: 'Open one' }),
      item({ id: 'id-b', description: 'Finished one', state: 'COMPLETED' }),
    ])
    renderItems([description], BOTH)

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select Open one' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Finished one' }))
    expect(screen.getByText('2 selected')).toBeVisible()

    // Narrow to the open work; the finished row's selection stops counting
    // rather than being carried into a bulk write the member cannot see.
    await userEvent.click(screen.getByRole('button', { name: /^Status:/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Just to do' }))

    expect(await screen.findByText('1 selected')).toBeVisible()
  })

  it('reopens a completed item, which takes its points back', async () => {
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([
      item({
        state: 'COMPLETED',
        pointsEarned: 50,
        completedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
      }),
    ])
    const reopen = vi.spyOn(itemsApi, 'setItemState').mockResolvedValue(item())
    renderItems([description], BOTH)

    await userEvent.click(await screen.findByRole('button', { name: 'Reopen Buy milk' }))

    await waitFor(() =>
      expect(reopen).toHaveBeenCalledWith('id-item', 3, 'OPEN', undefined),
    )
  })
})
