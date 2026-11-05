import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ActivitySection } from './ActivitySection'
import { itemKeys } from './queries'
import * as itemsApi from '../api/items'
import type { Activity } from '../api/items'
import { theme } from '../theme/theme'

function renderActivity() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <ActivitySection listId="id-list" />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

function activity(overrides: Partial<Activity> = {}): Activity {
  return {
    from: '2026-10-29T00:00:00Z',
    to: '2026-11-05T00:00:00Z',
    members: [],
    events: [],
    ...overrides,
  }
}

describe('ActivitySection', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(itemsApi, 'fetchPoints').mockResolvedValue([])
  })

  it('reports who finished how much, and what it was worth', async () => {
    vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(
      activity({
        members: [
          { userId: 'id-ben', displayName: 'Ben', avatarImageId: null, completed: 3, points: 90 },
          { userId: 'id-ana', displayName: 'Ana', avatarImageId: null, completed: 1, points: 0 },
        ],
        events: [
          {
            itemId: 'id-a',
            description: 'Bins out',
            completedAt: '2026-11-04T19:00:00Z',
            completedBy: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
            pointsEarned: 30,
            reopened: false,
          },
        ],
      }),
    )
    renderActivity()

    expect(await screen.findByText('Ben')).toBeVisible()
    expect(screen.getByText('3 items')).toBeVisible()
    expect(screen.getByText('90')).toBeVisible()
    // Singular, because "1 items" is the kind of thing a household notices.
    expect(screen.getByText('1 item')).toBeVisible()
    expect(screen.getByText('Bins out')).toBeVisible()
  })

  it('still shows a completion that was reopened since, and says so', async () => {
    // R49 kept the record so it could be seen; dropping it would mean a week's
    // history that changes retroactively whenever somebody reopens something.
    vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(
      activity({
        members: [
          { userId: 'id-ana', displayName: 'Ana', avatarImageId: null, completed: 1, points: 0 },
        ],
        events: [
          {
            itemId: 'id-a',
            description: 'Half done',
            completedAt: '2026-11-04T19:00:00Z',
            completedBy: { id: 'id-ana', displayName: 'Ana', avatarImageId: null },
            pointsEarned: null,
            reopened: true,
          },
        ],
      }),
    )
    renderActivity()

    expect(await screen.findByText('Half done')).toBeVisible()
    expect(screen.getByText('reopened since')).toBeVisible()
  })

  it('says plainly when nothing was finished', async () => {
    vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(activity())
    renderActivity()

    expect(
      await screen.findByText('Nothing finished on this list in the last seven days.'),
    ).toBeVisible()
  })

  it('asks for a seven-day window ending now', async () => {
    const fetch = vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(activity())
    renderActivity()

    await screen.findByText('Nothing finished on this list in the last seven days.')

    const [, from, to] = fetch.mock.calls[0]
    const days = (Date.parse(to) - Date.parse(from)) / 86_400_000
    // Seven days back, then to local midnight - so between seven and eight.
    expect(days).toBeGreaterThanOrEqual(7)
    expect(days).toBeLessThan(8)
  })

  it('refetches the week when an item is completed', async () => {
    // The activity key is a sibling of the items key, not a descendant, so
    // prefix invalidation of the items does not reach it. Getting that wrong
    // left the feed stale after a completion, and only the latency of a real
    // deployment made the race visible - hence this test rather than another
    // round of hoping.
    const fetch = vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(activity())
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme} defaultMode="light">
          <ActivitySection listId="id-list" />
        </ThemeProvider>
      </QueryClientProvider>,
    )
    await screen.findByText('Nothing finished on this list in the last seven days.')
    expect(fetch).toHaveBeenCalledOnce()

    // Exactly what useItemInvalidation does after a completion settles.
    await queryClient.invalidateQueries({ queryKey: itemKeys.activityAll('id-list') })

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
  })

  it('shows the all-time standing beside the week, not instead of it', async () => {
    // "This week" and "ever" are different questions and a household asks
    // both - "ever" stops being motivating around the third week.
    vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue(activity())
    vi.spyOn(itemsApi, 'fetchPoints').mockResolvedValue([
      { userId: 'id-ana', displayName: 'Ana', points: 420 },
    ])
    renderActivity()

    expect(await screen.findByRole('heading', { name: 'Points' })).toBeVisible()
    expect(screen.getByText('420')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'This week' })).toBeVisible()
  })
})
