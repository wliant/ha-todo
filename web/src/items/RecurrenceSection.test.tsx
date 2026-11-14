import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RecurrenceSection } from './RecurrenceSection'
import * as recurrenceApi from '../api/recurrence'
import * as authApi from '../api/auth'
import type { RecurrenceRule } from '../api/recurrence'
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
  members: [{ id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null }],
  createdAt: '2026-11-05T00:00:00Z',
}

const description: ListField = {
  id: 'id-desc',
  kind: 'DESCRIPTION',
  label: null,
  required: true,
  costMode: null,
  costUnitLabel: null,
  pointsPerUnit: null,
  choices: [],
}

function rule(overrides: Partial<RecurrenceRule> = {}): RecurrenceRule {
  return {
    id: 'id-rule',
    listId: 'id-list',
    description: 'Bins out',
    detailHtml: null,
    assignee: null,
    selfAssigned: false,
    frequency: 'WEEKLY',
    intervalCount: 1,
    daysOfWeek: ['TUESDAY'],
    dayOfMonth: null,
    dueTime: null,
    leadDays: 0,
    startsOn: '2026-11-01',
    endsOn: null,
    paused: false,
    lastMaterialisedOn: null,
    lastRunAt: null,
    lastError: null,
    costValues: [],
    createdBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
    editable: true,
    version: 2,
    ...overrides,
  }
}

function renderSection(owned = true, fields: ListField[] = [description]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <RecurrenceSection list={{ ...list, owned }} fields={fields} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('RecurrenceSection', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // Only so the author chip knows whose name not to print (0016 R142).
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      timezone: 'Asia/Singapore',
      avatarImageId: null,
    })
  })

  it('describes a rule in words a household would use', async () => {
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([rule()])
    renderSection()

    expect(await screen.findByText('Bins out')).toBeVisible()
    expect(screen.getByText('Tue')).toBeVisible()
  })

  it('says when every other week is meant', async () => {
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([
      rule({ intervalCount: 2, daysOfWeek: ['MONDAY'] }),
    ])
    renderSection()

    expect(await screen.findByText('every 2 weeks on Mon')).toBeVisible()
  })

  it('shows why a rule produced nothing, rather than failing in silence', async () => {
    // The whole risk of something that runs unattended (adr/0025).
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([
      rule({ lastError: 'Nobody could be assigned: Ben no longer has access.' }),
    ])
    renderSection()

    expect(
      await screen.findByText('Nobody could be assigned: Ben no longer has access.'),
    ).toBeVisible()
  })

  it('needs a weekday before a weekly rule can be saved', async () => {
    // The same rule the server enforces, so the member is told without a round
    // trip - the server is still the authority.
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([])
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a recurring item' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'What recurs' }), 'Bins')

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'TUESDAY' }))

    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  it('asks only for the parameters the chosen shape uses', async () => {
    // A day-of-month input beside a weekly rule is an invitation to set
    // something that will be ignored.
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([])
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a recurring item' }))
    expect(screen.getByRole('button', { name: 'TUESDAY' })).toBeVisible()
    expect(screen.queryByRole('spinbutton', { name: 'Day of the month' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: 'How often' }))
    await userEvent.click(screen.getByRole('option', { name: 'A day of the month' }))

    expect(screen.getByRole('spinbutton', { name: 'Day of the month' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'TUESDAY' })).not.toBeInTheDocument()
  })

  it('creates a rule with the schedule the member chose', async () => {
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([])
    const create = vi.spyOn(recurrenceApi, 'createRule').mockResolvedValue(rule())
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a recurring item' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'What recurs' }), 'Bins out')
    await userEvent.click(screen.getByRole('button', { name: 'TUESDAY' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-list',
        expect.objectContaining({
          description: 'Bins out',
          frequency: 'WEEKLY',
          daysOfWeek: ['TUESDAY'],
          intervalCount: 1,
        }),
      ),
    )
  })

  it('runs the rules now, and says what happened', async () => {
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([rule()])
    const run = vi.spyOn(recurrenceApi, 'runRules').mockResolvedValue({
      rulesScanned: 1,
      generated: 1,
      skipped: 0,
      failed: 0,
    })
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Run now' }))

    await waitFor(() => expect(run).toHaveBeenCalledWith('id-list'))
    expect(await screen.findByText('Added 1.')).toBeVisible()
  })

  it('says plainly when a run was not due rather than looking broken', async () => {
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([rule()])
    vi.spyOn(recurrenceApi, 'runRules').mockResolvedValue({
      rulesScanned: 1,
      generated: 0,
      skipped: 1,
      failed: 0,
    })
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Run now' }))

    expect(
      await screen.findByText('Nothing was due - everything is already on the list.'),
    ).toBeVisible()
  })

  it('lets a member write a standing instruction on a list they do not own', async () => {
    // 0016 R141: a rule only adds items, and adding items is what a grant has
    // always meant. The old rule was the opposite and was asserted here.
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([rule()])
    renderSection(false)

    expect(await screen.findByText('Bins out')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Run now' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Add a recurring item' })).toBeEnabled()
  })

  it('draws the edit control per rule, from the server’s answer', async () => {
    // R143/R144, and deliberately not recomputed from `owned` plus the
    // signed-in member: that would be a second copy of the predicate
    // (adr/0049). `owned` is true here and the unwritable rule still has no
    // control, which is what makes this test about `editable` and nothing else.
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([
      rule({ id: 'id-mine', description: 'Mine', editable: true }),
      rule({
        id: 'id-theirs',
        description: 'Theirs',
        editable: false,
        createdBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
      }),
    ])
    renderSection()

    expect(await screen.findByRole('button', { name: 'Edit Mine' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Edit Theirs' })).not.toBeInTheDocument()
  })

  it('names the member who set a rule up, but not when it is the caller', async () => {
    // A list of anonymous instructions reads as though the list made them
    // itself; "by Ana" on four of Ana's own rules teaches nothing (R142).
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([
      rule({ id: 'id-mine', description: 'Mine' }),
      rule({
        id: 'id-theirs',
        description: 'Theirs',
        createdBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
      }),
    ])
    renderSection()

    expect(await screen.findByText('by Ben')).toBeVisible()
    expect(screen.queryByText('by Ana')).not.toBeInTheDocument()
  })
})
