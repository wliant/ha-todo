import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CalendarFeeds } from './CalendarFeeds'
import * as calendarsApi from '../api/calendars'
import * as authApi from '../api/auth'
import * as listsApi from '../api/lists'
import type { CalendarFeed } from '../api/calendars'
import { theme } from '../theme/theme'

const SECRET = 'https://calendar.google.com/calendar/ical/abc123secret/basic.ics'

function feed(overrides: Partial<CalendarFeed> = {}): CalendarFeed {
  return {
    id: 'id-feed',
    label: 'Family',
    provider: 'GOOGLE',
    memberIds: [],
    lastSuccessAt: '2026-11-06T02:00:00Z',
    lastError: null,
    stale: false,
    eventCount: 4,
    ...overrides,
  }
}

function renderFeeds() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <CalendarFeeds />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('CalendarFeeds', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      timezone: 'Asia/Singapore',
      avatarImageId: null,
    })
    vi.spyOn(listsApi, 'fetchMembers').mockResolvedValue([
      { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
      { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
    ])
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([])
  })

  it('says nothing is subscribed rather than showing an empty box', async () => {
    renderFeeds()
    expect(await screen.findByText('You have not added a calendar yet.')).toBeVisible()
  })

  it('shows the member what came back before anything is saved', async () => {
    // R65: the only defence against publishing a whole working life is showing
    // it at the moment the member is paying attention.
    const preview = vi.spyOn(calendarsApi, 'previewCalendar').mockResolvedValue({
      provider: 'GOOGLE',
      totalInWindow: 7,
      next: [
        {
          id: null,
          summary: 'Interview with a recruiter',
          description: null,
          location: null,
          allDay: false,
          startsAt: '2026-11-07T11:30:00Z',
          endsAt: null,
          startDate: null,
          endDate: null,
          occursOn: '2026-11-07',
          sortMinute: 19 * 60 + 30,
          sources: [],
        },
      ],
    })
    const subscribe = vi.spyOn(calendarsApi, 'subscribeCalendar')
    renderFeeds()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a calendar' }))
    await userEvent.type(screen.getByLabelText('Calendar address'), SECRET)
    await userEvent.click(screen.getByRole('button', { name: 'Look at it' }))

    expect(await screen.findByText('Interview with a recruiter')).toBeVisible()
    expect(screen.getByText('and 6 more')).toBeVisible()
    expect(preview).toHaveBeenCalledWith(SECRET)
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('subscribes with the audience the member ticked, and nobody else', async () => {
    vi.spyOn(calendarsApi, 'previewCalendar').mockResolvedValue({
      provider: 'GOOGLE',
      totalInWindow: 0,
      next: [],
    })
    const subscribe = vi.spyOn(calendarsApi, 'subscribeCalendar').mockResolvedValue(feed())
    renderFeeds()

    await userEvent.click(await screen.findByRole('button', { name: 'Add a calendar' }))
    await userEvent.type(screen.getByLabelText('Calendar address'), SECRET)
    await userEvent.click(screen.getByRole('button', { name: 'Look at it' }))
    await screen.findByText('Who can see this?')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Ben' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add calendar' }))

    await waitFor(() =>
      expect(subscribe).toHaveBeenCalledWith({
        label: 'Google calendar',
        url: SECRET,
        memberIds: ['id-ben'],
      }),
    )
  })

  it('never renders the address, not even for the member who typed it', async () => {
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([feed()])
    const { container } = renderFeeds()

    await screen.findByText('Family')

    // adr/0035: the address is a bearer capability, and the server has no field
    // to return it in. One screenshot of this screen must not be a published
    // calendar.
    expect(container.innerHTML).not.toContain('abc123secret')
    expect(container.innerHTML).not.toContain('basic.ics')
  })

  it('tells the owner when a calendar has stopped working', async () => {
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([
      feed({ stale: true, lastError: 'The calendar was not found.', lastSuccessAt: null }),
    ])
    renderFeeds()

    expect(await screen.findByText('The calendar was not found.')).toBeVisible()
    expect(screen.getByText(/never updated/)).toBeVisible()
  })

  it('shares an existing calendar without being given the address again', async () => {
    // The only way to edit a feed: the address cannot be read back, so a PATCH
    // that left it out would otherwise have nothing to send.
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([feed()])
    const update = vi.spyOn(calendarsApi, 'updateCalendar').mockResolvedValue(feed())
    renderFeeds()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Ben' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith('id-feed', {
        label: 'Family',
        memberIds: ['id-ben'],
      }),
    )
  })

  it('warns what removing a calendar takes with it', async () => {
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([feed({ memberIds: ['id-ben'] })])
    const unsubscribe = vi.spyOn(calendarsApi, 'unsubscribeCalendar').mockResolvedValue()
    renderFeeds()

    await userEvent.click(await screen.findByRole('button', { name: 'Remove Family' }))

    expect(await screen.findByText(/takes its events off My day/)).toBeVisible()
    expect(unsubscribe).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() => expect(unsubscribe).toHaveBeenCalledWith('id-feed'))
  })

  it('refreshes on demand, because a published calendar lags its source', async () => {
    vi.spyOn(calendarsApi, 'fetchMyCalendars').mockResolvedValue([feed()])
    const refresh = vi.spyOn(calendarsApi, 'refreshCalendar').mockResolvedValue(feed())
    renderFeeds()

    await userEvent.click(await screen.findByRole('button', { name: 'Refresh Family' }))

    await waitFor(() => expect(refresh).toHaveBeenCalledWith('id-feed'))
  })
})
