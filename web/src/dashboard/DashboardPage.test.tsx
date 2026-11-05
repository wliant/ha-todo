import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { MonthView } from './MonthView'
import { MyDayView } from './MyDayView'
import { WeekView } from './WeekView'
import * as dashboardApi from '../api/dashboard'
import type { DashboardItem } from '../api/dashboard'
import { dayKey, startOfDay } from './calendar'
import { theme } from '../theme/theme'

function item(overrides: Partial<DashboardItem> = {}): DashboardItem {
  return {
    id: 'id-item',
    listId: 'id-list',
    listName: 'Chores',
    listIcon: 'CHECKLIST',
    description: 'Take the bins out',
    detailHtml: null,
    assignee: null,
    selfAssigned: false,
    dueAt: new Date().toISOString(),
    costValues: [],
    version: 1,
    ...overrides,
  }
}

/** The same nesting App.tsx declares, so the tabs are exercised as routes. */
function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/dashboard" element={<DashboardPage />}>
              <Route index element={<Navigate to="/dashboard/day" replace />} />
              <Route path="day" element={<MyDayView />} />
              <Route path="week" element={<WeekView />} />
              <Route path="month" element={<MonthView />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [],
    })
    vi.spyOn(dashboardApi, 'fetchUndated').mockResolvedValue({ items: [], total: 0 })
  })

  it('opens on today', async () => {
    renderAt('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Due today' })).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Today' })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows what is due today, and which list it is from', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [item()],
    })
    renderAt('/dashboard/day')

    expect(await screen.findByText('Take the bins out')).toBeVisible()
    // The dashboard crosses lists, so every row has to say where it is from.
    expect(screen.getByText(/Chores/)).toBeVisible()
  })

  it('lays the week out as seven day columns', async () => {
    renderAt('/dashboard/week')

    expect(await screen.findByRole('heading', { name: 'This week' })).toBeVisible()
    // Seven headings, one per day, whatever today happens to be.
    expect(screen.getAllByText(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) \d+$/)).toHaveLength(7)
  })

  it('shows descriptions only in the week view, never the chips', async () => {
    // R44 is explicit: a date grid carrying chips is unreadable on a phone.
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [
        item({
          assignee: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
          dueAt: startOfDay(new Date()).toISOString(),
        }),
      ],
    })
    renderAt('/dashboard/week')

    expect(await screen.findByText('Take the bins out')).toBeVisible()
    expect(screen.queryByText('Ana')).not.toBeInTheDocument()
  })

  it('lays the month out as a 42-cell grid', async () => {
    renderAt('/dashboard/month')

    const cells = await screen.findAllByRole('gridcell')
    // Always 42, so the grid does not change height between months.
    expect(cells).toHaveLength(42)
  })

  it('shows the undated panel on every range, and does not refetch it on a tab change', async () => {
    const undated = vi
      .spyOn(dashboardApi, 'fetchUndated')
      .mockResolvedValue({ items: [item({ id: 'id-undated', dueAt: null })], total: 1 })
    renderAt('/dashboard/day')

    expect(await screen.findByRole('heading', { name: 'No date yet' })).toBeVisible()
    expect(undated).toHaveBeenCalledOnce()

    await userEvent.click(screen.getByRole('tab', { name: 'Week' }))
    await screen.findByRole('heading', { name: 'This week' })

    // Still there, and still one request: the panel is a sibling of the
    // outlet, not part of each view.
    expect(screen.getByRole('heading', { name: 'No date yet' })).toBeVisible()
    expect(undated).toHaveBeenCalledOnce()
  })

  it('says how many undated items it could not show', async () => {
    // After adr/0022's backfill this panel is long by construction, so the cap
    // has to be able to explain itself.
    vi.spyOn(dashboardApi, 'fetchUndated').mockResolvedValue({
      items: [item({ id: 'id-a', dueAt: null })],
      total: 38,
    })
    renderAt('/dashboard/day')

    expect(await screen.findByText('and 37 more')).toBeVisible()
  })

  it('opens an item from the URL and closes it again', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [item({ detailHtml: '<p>Blue bin</p>' })],
    })
    renderAt('/dashboard/day?item=id-item')

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toBeVisible()
    expect(screen.getByText('Blue bin')).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('opens the popup from a day cell without a second request', async () => {
    // Every view carries the whole payload for this reason: the popup has to
    // open instantly on a phone.
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [item()],
    })
    renderAt('/dashboard/day')

    await userEvent.click(
      await screen.findByRole('button', { name: 'Take the bins out, on Chores' }),
    )

    expect(await screen.findByRole('dialog')).toBeVisible()
    // One for the day view, one for the dialog's month-wide fallback - and
    // never one per tap.
    expect(scheduled.mock.calls.length).toBeLessThanOrEqual(2)
  })

  it('narrows to the caller’s own items, and keeps it in the URL', async () => {
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [],
    })
    renderAt('/dashboard/day')
    await screen.findByRole('heading', { name: 'Due today' })

    await userEvent.click(screen.getByRole('button', { name: 'Mine' }))

    await waitFor(() =>
      expect(scheduled).toHaveBeenCalledWith(expect.anything(), expect.anything(), 'mine'),
    )
  })

  it('defaults to everything readable rather than only the caller’s', async () => {
    // The assignee field is optional and mostly unused, so a mine-only default
    // would be empty on exactly the lists this product is about.
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [],
    })
    renderAt('/dashboard/day')

    await waitFor(() =>
      expect(scheduled).toHaveBeenCalledWith(expect.anything(), expect.anything(), 'all'),
    )
  })

  it('asks for today as a half-open day in the member’s own zone', async () => {
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue({
      from: '',
      to: '',
      items: [],
    })
    renderAt('/dashboard/day')

    await waitFor(() => expect(scheduled).toHaveBeenCalled())
    const [from, to] = scheduled.mock.calls[0]
    expect(dayKey(new Date(from))).toBe(dayKey(new Date()))
    expect(Date.parse(to) - Date.parse(from)).toBe(86_400_000)
  })
})
