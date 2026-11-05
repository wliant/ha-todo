import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RecurrenceSection } from './RecurrenceSection'
import * as recurrenceApi from '../api/recurrence'
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

  it('lets a member who does not own the list look and run, but not write', async () => {
    // R20's reading: a standing instruction is the shape of the list, which is
    // the owner's. Running one is not.
    vi.spyOn(recurrenceApi, 'fetchRules').mockResolvedValue([rule()])
    renderSection(false)

    expect(await screen.findByText('Bins out')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Run now' })).toBeEnabled()
    expect(
      screen.queryByRole('button', { name: 'Add a recurring item' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Bins out' })).not.toBeInTheDocument()
  })
})
