import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it, vi } from 'vitest'
import { FilterBar } from './FilterBar'
import { DEFAULT_FILTER, UNASSIGNED, type ItemFilter } from './filters'
import type { ListField, ListMember } from '../api/lists'
import { theme } from '../theme/theme'

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
const assignee = field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })
const effort = field({
  id: 'id-effort',
  kind: 'COST',
  label: 'Effort',
  required: false,
  costMode: 'FIXED',
  choices: [
    { id: 'id-5', label: '5 min', points: 1 },
    { id: 'id-30', label: '30 min', points: 3 },
  ],
})

const members: ListMember[] = [
  { id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null },
  { id: 'id-ben', displayName: 'Ben', owner: false, avatarImageId: null },
]

function renderBar(fields: ListField[], filter: ItemFilter = DEFAULT_FILTER) {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <FilterBar filter={filter} fields={fields} members={members} onChange={onChange} />
    </ThemeProvider>,
  )
  return onChange
}

describe('FilterBar', () => {
  it('offers only what the list declares', () => {
    // R120: no assignee chip on a list without the field, and no cost chip for
    // a cost it does not have.
    renderBar([description])

    expect(screen.getByRole('button', { name: 'Status: To do' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Due: Any time' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /^Assignee/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Effort/ })).not.toBeInTheDocument()
  })

  it('draws a chip for each declared field, naming the selection', () => {
    renderBar([description, assignee, effort], {
      ...DEFAULT_FILTER,
      assigneeIds: ['id-ben'],
      due: { kind: 'overdue' },
    })

    expect(screen.getByRole('button', { name: 'Assignee: Ben' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Due: Overdue' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Effort: Any' })).toBeVisible()
  })

  it('announces no filter on an untouched list', () => {
    // R107's count is measured against the default, so the view a member has
    // not touched says nothing rather than "1 filter".
    renderBar([description, assignee])

    expect(screen.queryByText(/filter/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
  })

  it('counts the active filters and offers Clear', async () => {
    const onChange = renderBar([description, assignee], {
      ...DEFAULT_FILTER,
      status: ['done'],
      assigneeIds: [UNASSIGNED],
    })

    expect(screen.getByRole('status')).toHaveTextContent('2 filters')

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))

    expect(onChange).toHaveBeenCalledWith(DEFAULT_FILTER)
  })

  it('picks statuses independently rather than exclusively', async () => {
    // R108, the whole point of adr/0046: both at once is reachable.
    const onChange = renderBar([description])

    await userEvent.click(screen.getByRole('button', { name: 'Status: To do' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Done' }))

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['open', 'done'] }),
    )
  })

  it('offers unassigned beside the members', async () => {
    // R109: work nobody has taken is the case this filter exists for, and it
    // is not a member - so it is offered rather than inferred.
    const onChange = renderBar([description, assignee])

    await userEvent.click(screen.getByRole('button', { name: 'Assignee: Anyone' }))
    expect(screen.getByRole('menuitem', { name: 'Ana' })).toBeVisible()
    await userEvent.click(screen.getByRole('menuitem', { name: 'Unassigned' }))

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ assigneeIds: [UNASSIGNED] }),
    )
  })

  it('offers a cost’s own choices, and the items that answered none', async () => {
    const onChange = renderBar([description, effort])

    await userEvent.click(screen.getByRole('button', { name: 'Effort: Any' }))
    expect(screen.getByRole('menuitem', { name: '5 min' })).toBeVisible()
    expect(screen.getByRole('menuitem', { name: '30 min' })).toBeVisible()
    await userEvent.click(screen.getByRole('menuitem', { name: 'Not answered' }))

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        costs: { 'id-effort': expect.objectContaining({ unanswered: true }) },
      }),
    )
  })

  it('offers a numeric cost a range rather than choices', async () => {
    const money = field({
      id: 'id-money',
      kind: 'COST',
      label: 'Money',
      required: false,
      costMode: 'NUMERIC',
      costUnitLabel: 'dollars',
    })
    const onChange = renderBar([description, money])

    await userEvent.click(screen.getByRole('button', { name: 'Money: Any' }))
    await userEvent.type(screen.getByLabelText('At least'), '5')

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ costs: { 'id-money': expect.objectContaining({ min: 5 }) } }),
    )
  })

  it('sorts by one key and flips the direction', async () => {
    const onChange = renderBar([description])

    await userEvent.click(screen.getByRole('button', { name: 'Sort: Manual order' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Due date' }))
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ sort: 'due' }))

    await userEvent.click(screen.getByRole('button', { name: 'Sort descending' }))
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ descending: true }))
  })
})
