import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { MonthView } from './MonthView'
import { MyDayView } from './MyDayView'
import { WeekView } from './WeekView'
import * as dashboardApi from '../api/dashboard'
import type { DashboardItem } from '../api/dashboard'
import type { CalendarEvent } from '../api/calendars'
import { addDays, civilIn, dayKey, startOfDay } from './calendar'
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

function event(overrides: Partial<CalendarEvent> = {}): CalendarEvent {
  return {
    id: 'id-event',
    summary: 'Parents evening',
    description: null,
    location: null,
    allDay: false,
    startsAt: new Date().toISOString(),
    endsAt: null,
    startDate: null,
    endDate: null,
    occursOn: dayKey(civilIn(new Date(), 'Asia/Singapore')),
    sortMinute: 19 * 60 + 30,
    sources: [
      {
        feedId: 'id-feed',
        label: 'Family',
        memberId: 'id-ana',
        memberDisplayName: 'Ana',
        avatarImageId: null,
      },
    ],
    ...overrides,
  }
}

/** One payload shape in one place: Scheduled gains fields over time. */
function payload(overrides: Partial<dashboardApi.Scheduled> = {}): dashboardApi.Scheduled {
  return {
    from: '',
    to: '',
    items: [],
    events: [],
    staleFeeds: [],
    // Wide enough that the out-of-window notice stays out of the way unless a
    // test is about it.
    eventWindow: { from: '2000-01-01', to: '2099-12-31' },
    ...overrides,
  }
}

/** MemoryRouter keeps its own history, so the address has to be read from it. */
function LocationProbe() {
  const { pathname, search } = useLocation()
  return <output data-testid="address">{pathname + search}</output>
}

/** How the controls name a day that is not today. */
function dayName(day: Date): string {
  return day.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
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
          <LocationProbe />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload())
    vi.spyOn(dashboardApi, 'fetchUndated').mockResolvedValue({ items: [], total: 0 })
  })

  it('opens on today', async () => {
    renderAt('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Today' })).toBeVisible()
    // Named for the range, not for the present (0008 R73).
    expect(screen.getByRole('tab', { name: 'Day' })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows what is due today, and which list it is from', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload({ items: [item()] }))
    renderAt('/dashboard/day')

    expect(await screen.findByText('Take the bins out')).toBeVisible()
    // The dashboard crosses lists, so every row has to say where it is from.
    expect(screen.getByText(/Chores/)).toBeVisible()
  })

  it('lays the week out as seven day columns', async () => {
    renderAt('/dashboard/week')

    expect(await screen.findByRole('button', { name: 'This week' })).toBeInTheDocument()
    // Seven headings, one per day, whatever today happens to be.
    expect(screen.getAllByText(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) \d+$/)).toHaveLength(7)
  })

  it('shows descriptions only in the week view, never the chips', async () => {
    // R44 is explicit: a date grid carrying chips is unreadable on a phone.
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload({
      items: [
        item({
          assignee: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
          dueAt: startOfDay(new Date()).toISOString(),
        }),
      ],
    }))
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
    await screen.findByRole('button', { name: 'Next week' })

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
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload({ items: [item({ detailHtml: '<p>Blue bin</p>' })] }))
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
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload({ items: [item()] }))
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
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload())
    renderAt('/dashboard/day')
    await screen.findByRole('heading', { name: 'Today' })

    await userEvent.click(screen.getByRole('button', { name: 'Mine' }))

    await waitFor(() =>
      expect(scheduled).toHaveBeenCalledWith(expect.anything(), expect.anything(), 'mine'),
    )
  })

  it('defaults to everything readable rather than only the caller’s', async () => {
    // The assignee field is optional and mostly unused, so a mine-only default
    // would be empty on exactly the lists this product is about.
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload())
    renderAt('/dashboard/day')

    await waitFor(() =>
      expect(scheduled).toHaveBeenCalledWith(expect.anything(), expect.anything(), 'all'),
    )
  })

  it('shows an event beside the chores rather than in a section of its own', async () => {
    // 0007 R67: the question is "what is Thursday like", not "what are my
    // chores and separately what are my appointments".
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
      payload({ items: [item()], events: [event()] }),
    )
    renderAt('/dashboard/day')

    expect(await screen.findByText('Take the bins out')).toBeVisible()
    expect(screen.getByText('Parents evening')).toBeVisible()
  })

  it('puts an all-day event above the day’s timed rows', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
      payload({
        items: [item()],
        events: [
          event({
            id: 'id-allday',
            summary: 'Ben away',
            allDay: true,
            startsAt: null,
            sortMinute: -1,
          }),
        ],
      }),
    )
    renderAt('/dashboard/day')

    await screen.findByText('Ben away')
    const rendered = screen.getAllByText(/Ben away|Take the bins out/)
    expect(rendered[0]).toHaveTextContent('Ben away')
  })

  it('opens an event read-only, with no way to do anything to it', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
      payload({ events: [event({ location: 'The hall' })] }),
    )
    renderAt('/dashboard/day')

    await userEvent.click(await screen.findByRole('button', { name: /Parents evening/ }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('The hall')
    expect(dialog).toHaveTextContent('Ana · Family')
    // Nothing to complete, claim or edit: it is somebody else's calendar.
    expect(screen.queryByRole('button', { name: /Complete|Claim|Save/ })).not.toBeInTheDocument()
  })

  it('describes an all-day event by its date and never by a time', async () => {
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
      payload({
        events: [
          event({
            summary: 'School play',
            allDay: true,
            startsAt: null,
            endsAt: null,
            startDate: '2026-11-07',
            endDate: '2026-11-08',
            occursOn: '2026-11-07',
            sortMinute: -1,
          }),
        ],
      }),
    )
    renderAt('/dashboard/month')

    await userEvent.click(await screen.findByRole('button', { name: /School play/ }))

    const dialog = await screen.findByRole('dialog')
    // Loose about the locale's punctuation, strict about the two things that
    // matter: it has no time, and it is one day.
    expect(dialog).toHaveTextContent(/All day, Saturday,? 7 November/)
    // DTEND is exclusive, so a one-day event must not read as two.
    expect(dialog).not.toHaveTextContent(/to Sunday,? 8 November/)
  })

  it('says so when a calendar has stopped refreshing', async () => {
    // Without this an empty Thursday is a lie: it means both "nothing is due"
    // and "nothing is due and no feed told us otherwise" (R71).
    vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
      payload({
        staleFeeds: [
          {
            feedId: 'id-feed',
            label: 'Work',
            memberDisplayName: 'Ben',
            lastSuccessAt: '2026-11-03T02:00:00Z',
            lastError: 'The calendar was not found.',
          },
        ],
      }),
    )
    renderAt('/dashboard/day')

    const notice = await screen.findByText(/Work calendar/)
    expect(notice).toHaveTextContent('Ben')
    expect(await screen.findByText('Some events may be missing')).toBeVisible()
  })

  describe('moving between periods (0008)', () => {
    it('steps a day at a time and says where it landed', async () => {
      renderAt('/dashboard/day')
      await screen.findByRole('heading', { name: 'Today' })

      await userEvent.click(screen.getByRole('button', { name: 'Next day' }))

      const tomorrow = addDays(civilIn(new Date(), 'Asia/Singapore'), 1)
      // Both the control and the view's own heading say it, on purpose.
      expect(await screen.findByRole('heading', { name: dayName(tomorrow) })).toBeVisible()
      expect(screen.queryByRole('heading', { name: 'Today' })).not.toBeInTheDocument()
    })

    it('puts the period in the address, so a link to a day is a link', async () => {
      renderAt('/dashboard/day')
      await screen.findByRole('heading', { name: 'Today' })

      await userEvent.click(await screen.findByRole('button', { name: 'Next day' }))

      const tomorrow = addDays(civilIn(new Date(), 'Asia/Singapore'), 1)
      await waitFor(() =>
        expect(screen.getByTestId('address')).toHaveTextContent(`on=${dayKey(tomorrow)}`),
      )
    })

    it('opens on whatever day the address names', async () => {
      renderAt('/dashboard/day?on=2026-02-11')

      expect(await screen.findByRole('heading', { name: /11 February/ })).toBeVisible()
    })

    it('falls back to today rather than breaking on a mangled address', async () => {
      // The landing page: a truncated or edited ?on= must not be a member who
      // cannot get into the product.
      renderAt('/dashboard/day?on=2026-13-45')

      expect(await screen.findByRole('heading', { name: 'Today' })).toBeVisible()
    })

    it('keeps the period when the range changes', async () => {
      renderAt('/dashboard/day?on=2026-02-11')
      await screen.findByRole('heading', { name: /11 February/ })

      await userEvent.click(screen.getByRole('tab', { name: 'Month' }))

      expect(await screen.findByRole('heading', { name: /February 2026/ })).toBeVisible()
      // and the address still carries it, so the link is still a link
      expect(screen.getByTestId('address')).toHaveTextContent('on=2026-02-11')
    })

    it('offers the way back only when there is somewhere to go back from', async () => {
      renderAt('/dashboard/day')

      expect(await screen.findByRole('button', { name: 'Today' })).toBeDisabled()

      await userEvent.click(screen.getByRole('button', { name: 'Previous day' }))
      const back = await screen.findByRole('button', { name: 'Today' })
      expect(back).toBeEnabled()

      await userEvent.click(back)
      expect(await screen.findByRole('heading', { name: 'Today' })).toBeVisible()
    })

    it('highlights today by the clock, never by the period on screen', async () => {
      // 0008's first consequence, and the one mistake this feature made
      // available: layout follows the anchor, "now" follows the clock.
      renderAt('/dashboard/month?on=2026-02-11')
      await screen.findByRole('heading', { name: /February 2026/ })

      const todaysNumber = String(civilIn(new Date(), 'Asia/Singapore').getDate())
      const cells = screen.getAllByRole('gridcell')
      const highlighted = cells.filter((cell) =>
        cell.querySelector('.MuiTypography-root')?.className.includes('primary'),
      )
      // February 2026 is not this month, so nothing in it is today.
      expect(highlighted).toHaveLength(0)
      expect(todaysNumber).toBeTruthy()
    })

    it('says so when the member steps past what has been read', async () => {
      vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
        payload({ eventWindow: { from: '2026-09-22', to: '2026-12-31' } }),
      )
      renderAt('/dashboard/month?on=2027-06-15')

      // An empty June must not look like a quiet June (R77).
      expect(await screen.findByText(/No calendar events have been read/)).toBeVisible()
    })

    it('stays quiet inside the window', async () => {
      vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
        payload({ eventWindow: { from: '2000-01-01', to: '2099-12-31' } }),
      )
      renderAt('/dashboard/month')

      await screen.findByRole('tab', { name: 'Month' })
      expect(screen.queryByText(/have been read/)).not.toBeInTheDocument()
    })
  })

  it('asks for today as a half-open day in the household’s zone', async () => {
    const scheduled = vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(payload())
    renderAt('/dashboard/day')

    await waitFor(() => expect(scheduled).toHaveBeenCalled())
    const [from, to] = scheduled.mock.calls[0]
    // Midnight where the household is, whatever zone this test runs in: the
    // signed-in user's timezone is Asia/Singapore in these fixtures.
    expect(dayKey(civilIn(new Date(from), 'Asia/Singapore'))).toBe(
      dayKey(civilIn(new Date(), 'Asia/Singapore')),
    )
    expect(new Date(from).toISOString()).toMatch(/T16:00:00/)
    expect(Date.parse(to) - Date.parse(from)).toBe(86_400_000)
  })
})
