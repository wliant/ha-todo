import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { MonthView } from './MonthView'
import { MyDayView } from './MyDayView'
import { WeekView } from './WeekView'
import * as dashboardApi from '../api/dashboard'
import * as itemsApi from '../api/items'
import type { DashboardItem } from '../api/dashboard'
import type { CalendarEvent } from '../api/calendars'
import { addDays, civilIn, dayKey, instantOf, startOfDay } from './calendar'

/** The household's zone in these fixtures, as /api/me reports it. */
const ZONE = 'Asia/Singapore'
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
    vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({ items: [], total: 0 })
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

  /**
   * Completing a chore without leaving the agenda (0010 R83-R87).
   *
   * <p>The write is the item's own endpoint, so what these assert is the
   * wiring: the right item, the version the payload carried, the note on the
   * same request, and that a row nobody can see any more still gets its
   * conflict said out loud.
   */
  describe('completing a chore', () => {
    /** The payload the agenda was drawn from, and nothing after it. */
    function thenEmpty() {
      return vi
        .spyOn(dashboardApi, 'fetchScheduled')
        .mockResolvedValueOnce(payload({ items: [item({ version: 7 })] }))
        .mockResolvedValue(payload())
    }

    it('ticks a chore off from the day’s row', async () => {
      thenEmpty()
      const complete = vi
        .spyOn(itemsApi, 'setItemCompleted')
        .mockResolvedValue({} as unknown as itemsApi.Item)
      renderAt('/dashboard/day')

      await userEvent.click(
        await screen.findByRole('button', { name: 'Complete Take the bins out' }),
      )

      // The version the row was drawn from, so a stale tap is a 409 rather
      // than an overwrite.
      expect(complete).toHaveBeenCalledWith('id-item', 7, true, undefined)
      // The agenda shows outstanding work, so the row leaves (R86).
      await waitFor(() =>
        expect(screen.queryByText('Take the bins out')).not.toBeInTheDocument(),
      )
      expect(await screen.findByText('Take the bins out is done.')).toBeVisible()
    })

    it('carries the note on the same request', async () => {
      thenEmpty()
      const complete = vi
        .spyOn(itemsApi, 'setItemCompleted')
        .mockResolvedValue({} as unknown as itemsApi.Item)
      renderAt('/dashboard/day')

      await userEvent.click(
        await screen.findByRole('button', { name: 'Complete Take the bins out with a note' }),
      )
      // The editor is lazy-loaded; the dialog's own Complete is what sends it.
      const dialog = await screen.findByRole('dialog')
      await userEvent.click(await within(dialog).findByRole('button', { name: 'Complete' }))

      // One gesture, one request - never a completion followed by a note.
      await waitFor(() => expect(complete).toHaveBeenCalledOnce())
      expect(complete).toHaveBeenCalledWith('id-item', 7, true, null)
    })

    it('completes from the popup, and closes it', async () => {
      // R85: this is what makes the week view, the month view and the undated
      // panel able to complete anything at all.
      // Not thenEmpty(): the popup reads the anchor's *month* window, which is
      // a second key and therefore a second call.
      vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
        payload({ items: [item({ version: 7 })] }),
      )
      const complete = vi
        .spyOn(itemsApi, 'setItemCompleted')
        .mockResolvedValue({} as unknown as itemsApi.Item)
      renderAt('/dashboard/week?item=id-item')

      const dialog = await screen.findByRole('dialog')
      await userEvent.click(
        within(dialog).getByRole('button', { name: 'Complete Take the bins out' }),
      )

      expect(complete).toHaveBeenCalledWith('id-item', 7, true, undefined)
      // Its subject has stopped existing; a dialog describing nothing must not
      // be left on screen.
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('says who got there first, and puts the row back', async () => {
      // R87, and the reason the optimistic removal is reversible.
      vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
        payload({ items: [item({ version: 7 })] }),
      )
      vi.spyOn(itemsApi, 'setItemCompleted').mockRejectedValue({
        isAxiosError: true,
        response: {
          status: 409,
          data: { code: 'STALE_ITEM', error: 'Ben completed this first.', item: {} },
        },
      })
      renderAt('/dashboard/day')

      await userEvent.click(
        await screen.findByRole('button', { name: 'Complete Take the bins out' }),
      )

      expect(await screen.findByText('Ben completed this first.')).toBeVisible()
      expect(screen.getByText('Take the bins out')).toBeVisible()
    })

    it('leaves an event with nothing to complete', async () => {
      // The two must read differently, on a screen where one of them now has
      // a completion control (0007 R63).
      vi.spyOn(dashboardApi, 'fetchScheduled').mockResolvedValue(
        payload({ items: [item()], events: [event()] }),
      )
      renderAt('/dashboard/day')

      await screen.findByText('Parents evening')
      // One control, for the chore - not two.
      expect(screen.getAllByRole('button', { name: /^Complete / })).toHaveLength(2)
      expect(
        screen.getAllByRole('button', { name: /^Complete / }).map((node) => node.ariaLabel),
      ).toEqual([
        'Complete Take the bins out',
        'Complete Take the bins out with a note',
      ])
    })
  })


  describe('overdue work (0011 R88-R93)', () => {
    /** Days before today, as an instant the household reads as that day. */
    function daysAgo(days: number): string {
      return new Date(
        instantOf(addDays(startOfDay(civilIn(new Date(), ZONE)), -days), ZONE),
      ).toISOString()
    }

    function late(days: number, overrides: Partial<DashboardItem> = {}) {
      return item({
        id: `id-late-${days}`,
        description: `Missed by ${days}`,
        dueAt: daysAgo(days),
        ...overrides,
      })
    }

    it('says nothing at all when nothing is late', async () => {
      renderAt('/dashboard/day')

      await screen.findByRole('heading', { name: 'Today' })
      // Not "nothing is overdue": this sits above the grid on every view, and a
      // household that is keeping up should never see it.
      expect(screen.queryByText(/overdue/)).not.toBeInTheDocument()
    })

    it('leads with a count, and expands to the chores behind it', async () => {
      vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(2)],
        total: 1,
      })
      renderAt('/dashboard/day')

      expect(await screen.findByText('1 item is overdue')).toBeVisible()
      // Collapsed by default - the count is the whole message until asked.
      expect(screen.queryByText('Missed by 2')).not.toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Show' }))

      expect(await screen.findByText('Missed by 2')).toBeVisible()
      expect(screen.getByText(/Chores · due 2 days ago/)).toBeVisible()
    })

    it('says how much it is not showing', async () => {
      // R92. The first read of this in any household is every item ever dated
      // and never finished, so the page is capped and the total is the truth.
      vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(1)],
        total: 38,
      })
      renderAt('/dashboard/day')

      expect(await screen.findByText('38 items are overdue')).toBeVisible()
      await userEvent.click(screen.getByRole('button', { name: 'Show' }))
      expect(await screen.findByText('and 37 more')).toBeVisible()
    })

    it('does not change when the member navigates to another period', async () => {
      // R89, which is the requirement most easily lost: anything that passes
      // the period into this read makes "overdue" mean "before February".
      const fetched = vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(3)],
        total: 1,
      })
      renderAt('/dashboard/month')

      expect(await screen.findByText('1 item is overdue')).toBeVisible()
      expect(fetched).toHaveBeenCalledOnce()
      expect(fetched).toHaveBeenCalledWith('all')

      await userEvent.click(screen.getByRole('button', { name: 'Next month' }))

      expect(await screen.findByText('1 item is overdue')).toBeVisible()
      // One cache entry for every period, so stepping through months is not a
      // request - and cannot be a different answer.
      expect(fetched).toHaveBeenCalledOnce()
    })

    it('is on every range, not only the day', async () => {
      vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(4)],
        total: 1,
      })
      renderAt('/dashboard/week')

      expect(await screen.findByText('1 item is overdue')).toBeVisible()
    })

    it('narrows with the scope', async () => {
      const fetched = vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [],
        total: 0,
      })
      renderAt('/dashboard/day?scope=mine')

      await waitFor(() => expect(fetched).toHaveBeenCalledWith('mine'))
    })

    it('completes a late chore from the strip, with the version it was drawn from', async () => {
      // R90. The same write the Day row makes, which is the point of reusing
      // the mutation rather than adding one here.
      vi.spyOn(dashboardApi, 'fetchOverdue')
        // Once with the chore, then without it: the strip's own invalidation
        // refetches, and a mock that kept returning it would be asserting that
        // the optimistic removal is undone.
        .mockResolvedValueOnce({ items: [late(2, { version: 9 })], total: 1 })
        .mockResolvedValue({ items: [], total: 0 })
      const completed = vi
        .spyOn(itemsApi, 'setItemCompleted')
        .mockResolvedValue({} as never)
      renderAt('/dashboard/day')

      await userEvent.click(await screen.findByRole('button', { name: 'Show' }))
      await userEvent.click(
        await screen.findByRole('button', { name: 'Complete Missed by 2' }),
      )

      expect(completed).toHaveBeenCalledWith('id-late-2', 9, true, undefined)
      // Optimistic in one direction only: the row leaves, and the strip with
      // it, because the total it was counting went too.
      await waitFor(() => expect(screen.queryByText('Missed by 2')).not.toBeInTheDocument())
      await waitFor(() => expect(screen.queryByText(/overdue/)).not.toBeInTheDocument())
    })

    it('takes the row out before the server answers', async () => {
      // The assertion that the mutation's own `onMutate` knows about this
      // payload. Written against a completion that never resolves, because a
      // test that lets the refetch land passes whether or not the optimistic
      // update exists - which is exactly the defect adr/0040 warns is
      // invisible.
      vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(2, { version: 9 })],
        total: 1,
      })
      vi.spyOn(itemsApi, 'setItemCompleted').mockReturnValue(new Promise(() => {}))
      renderAt('/dashboard/day')

      await userEvent.click(await screen.findByRole('button', { name: 'Show' }))
      await userEvent.click(
        await screen.findByRole('button', { name: 'Complete Missed by 2' }),
      )

      await waitFor(() => expect(screen.queryByText('Missed by 2')).not.toBeInTheDocument())
      // And the strip goes with it: the total it was counting lost a chore.
      expect(screen.queryByText(/overdue/)).not.toBeInTheDocument()
    })

    it('says how late in words a person would use', async () => {
      vi.spyOn(dashboardApi, 'fetchOverdue').mockResolvedValue({
        items: [late(1), late(3), late(20)],
        total: 3,
      })
      renderAt('/dashboard/day')

      await userEvent.click(await screen.findByRole('button', { name: 'Show' }))

      expect(await screen.findByText(/due yesterday/)).toBeVisible()
      expect(screen.getByText(/due 3 days ago/)).toBeVisible()
      // Beyond a week the count stops being useful and the date starts.
      const old = new Date(instantOf(addDays(startOfDay(civilIn(new Date(), ZONE)), -20), ZONE))
      const asDate = civilIn(old, ZONE).toLocaleDateString(undefined, {
        day: 'numeric',
        month: 'short',
      })
      expect(screen.getByText(new RegExp(`due ${asDate}`))).toBeVisible()
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
