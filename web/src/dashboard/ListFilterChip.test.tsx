import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ListFilterChip } from './ListFilterChip'
import * as listsApi from '../api/lists'
import type { ListSummary } from '../api/lists'

function summary(id: string, name: string): ListSummary {
  return { id, name, description: null, icon: 'CHECKLIST', owned: true, pinned: false, memberCount: 1 }
}

function renderChip(picked: string[]) {
  const onChange = vi.fn()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ListFilterChip picked={picked} onChange={onChange} />
    </QueryClientProvider>,
  )
  return onChange
}

describe('ListFilterChip', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(listsApi, 'fetchLists').mockResolvedValue([
      summary('id-chores', 'Chores'),
      summary('id-groceries', 'Groceries'),
    ])
  })

  it('offers every list the member can see', async () => {
    renderChip([])

    await userEvent.click(screen.getByRole('button', { name: 'Lists' }))

    // Not the sidebar's: that one is capped at a preference and partitioned
    // against the pinned section, so it is not the full set.
    expect(await screen.findByRole('menuitem', { name: 'Chores' })).toBeVisible()
    expect(screen.getByRole('menuitem', { name: 'Groceries' })).toBeVisible()
  })

  it('adds a list to the selection rather than replacing it', async () => {
    const onChange = renderChip(['id-chores'])

    await userEvent.click(await screen.findByRole('button', { name: 'Lists: Chores' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Groceries' }))

    expect(onChange).toHaveBeenCalledWith(['id-chores', 'id-groceries'])
  })

  it('unticks a list that was already chosen', async () => {
    const onChange = renderChip(['id-chores', 'id-groceries'])

    await userEvent.click(screen.getByRole('button', { name: 'Lists: 2' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Groceries' }))

    expect(onChange).toHaveBeenCalledWith(['id-chores'])
  })

  it('clears back to every list', async () => {
    // The way back is always one tap, and it is named rather than being the
    // absence of ticks (R124).
    const onChange = renderChip(['id-chores'])

    await userEvent.click(await screen.findByRole('button', { name: 'Lists: Chores' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'All lists' }))

    expect(onChange).toHaveBeenCalledWith([])
  })

  it('is on screen before the lists arrive', async () => {
    // R124: an agenda showing less than it holds must never look like an
    // agenda that holds that little, so the control never waits for a fetch.
    vi.spyOn(listsApi, 'fetchLists').mockReturnValue(new Promise(() => {}))
    renderChip([])

    expect(screen.getByRole('button', { name: 'Lists' })).toBeVisible()
  })
})
