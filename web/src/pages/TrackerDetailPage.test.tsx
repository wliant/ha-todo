import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TrackerDetailPage } from './TrackerDetailPage'
import * as trackersApi from '../api/trackers'
import * as readingsApi from '../api/trackerReadings'
import type { TrackerDetail, TrackerSeries } from '../api/trackers'
import type { Reading } from '../api/trackerReadings'
import { theme } from '../theme/theme'

function tracker(overrides: Partial<TrackerDetail> = {}): TrackerDetail {
  return {
    id: 'id-t',
    name: 'Body Weight',
    description: null,
    icon: 'HEALTH',
    unit: 'kg',
    period: 'DAY',
    scope: 'PER_MEMBER',
    rollup: 'LATEST',
    decimals: 1,
    target: null,
    targetDirection: null,
    owned: true,
    pinned: false,
    reconfigurable: false,
    version: 1,
    members: [{ id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null }],
    createdAt: '2026-11-01T00:00:00Z',
    ...overrides,
  }
}

function series(overrides: Partial<TrackerSeries> = {}): TrackerSeries {
  return {
    trackerId: 'id-t',
    unit: 'kg',
    period: 'DAY',
    rollup: 'LATEST',
    scope: 'PER_MEMBER',
    decimals: 1,
    target: null,
    targetDirection: null,
    by: 'DAY',
    from: '2026-11-17',
    to: '2026-11-20',
    buckets: ['2026-11-17', '2026-11-19'],
    series: [
      { memberId: 'id-ana', displayName: 'Ana', avatarImageId: null, values: [72.9, 72.4] },
    ],
    ...overrides,
  }
}

function reading(overrides: Partial<Reading> = {}): Reading {
  return {
    id: 'id-r',
    trackerId: 'id-t',
    memberId: 'id-ana',
    memberDisplayName: 'Ana',
    memberAvatarImageId: null,
    periodStart: '2026-11-19',
    amount: 72.4,
    note: null,
    recordedByDisplayName: 'Ana',
    recordedAt: '2026-11-19T07:10:00Z',
    editable: true,
    version: 0,
    ...overrides,
  }
}

/** MemoryRouter keeps its own history, so the address has to be read from it. */
function LocationProbe() {
  const { search } = useLocation()
  return <output data-testid="address">{search}</output>
}

function renderAt(path = '/trackers/id-t') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/trackers/:trackerId" element={<TrackerDetailPage />} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('TrackerDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(trackersApi, 'fetchTracker').mockResolvedValue(tracker())
    vi.spyOn(trackersApi, 'fetchSeries').mockResolvedValue(series())
    vi.spyOn(trackersApi, 'touchTracker').mockResolvedValue(undefined)
    vi.spyOn(readingsApi, 'fetchReadings').mockResolvedValue([reading()])
  })

  it('shows the latest figure, its unit and which way it moved', async () => {
    renderAt()

    // The headline, before the lazy chart has had a chance to draw axis ticks
    // that would also read "72.4".
    expect(await screen.findByText('72.4')).toBeVisible()
    expect(screen.getAllByText('kg').length).toBeGreaterThan(0)
    // 72.9 -> 72.4, so down by half a kilo.
    expect(screen.getByText('−0.5 kg')).toBeVisible()
  })

  it('says how far the latest figure is from the target', async () => {
    vi.spyOn(trackersApi, 'fetchSeries').mockResolvedValue(
      series({ target: 70, targetDirection: 'AT_MOST' }),
    )
    renderAt()

    expect(await screen.findByText('2.4 kg off target')).toBeVisible()
  })

  /**
   * Three states for the chart region. A failed request that said "nothing
   * recorded" would be a confident lie about somebody's own data - the same
   * mistake `NavGroup` shipped with in 0012.
   */
  it('does not claim nothing is recorded while the numbers are still loading', async () => {
    vi.spyOn(trackersApi, 'fetchSeries').mockReturnValue(new Promise(() => {}))
    renderAt()

    expect(await screen.findByRole('heading', { name: 'Body Weight' })).toBeVisible()
    expect(screen.queryByText('Nothing recorded in this period.')).not.toBeInTheDocument()
  })

  it('says so when the numbers cannot be read, rather than saying there are none', async () => {
    vi.spyOn(trackersApi, 'fetchSeries').mockRejectedValue(new Error('offline'))
    renderAt()

    expect(await screen.findByText('Could not read this tracker’s numbers.')).toBeVisible()
    expect(screen.queryByText('Nothing recorded in this period.')).not.toBeInTheDocument()
  })

  it('keeps the range in the address, and leaves the default out of it', async () => {
    renderAt()
    // Waited on by its heading, not by a figure: once the lazy chart lands it
    // draws axis ticks, and a tick reading "72.4" makes a text query for the
    // headline ambiguous. Nothing here asserts on the chart's own markup
    // (adr/0053) and nothing should wait on it either.
    await screen.findByRole('heading', { name: 'Body Weight' })

    await userEvent.click(screen.getByRole('button', { name: '30 days' }))

    await waitFor(() =>
      expect(screen.getByTestId('address')).toHaveTextContent('range=30d'),
    )

    await userEvent.click(screen.getByRole('button', { name: '3 months' }))

    // The default writes nothing, the rule `?on=` and the list's filter both
    // follow (adr/0037, adr/0045).
    await waitFor(() => expect(screen.getByTestId('address')).toHaveTextContent(''))
    expect(screen.getByTestId('address')).not.toHaveTextContent('range')
  })

  it('draws a year of a daily tracker by month rather than as 365 points', async () => {
    const fetched = vi.spyOn(trackersApi, 'fetchSeries').mockResolvedValue(series())
    renderAt('/trackers/id-t?range=1y')

    await waitFor(() =>
      expect(fetched).toHaveBeenCalledWith('id-t', expect.objectContaining({ by: 'MONTH' })),
    )
  })

  it('offers edit and delete only on a reading the caller may correct', async () => {
    vi.spyOn(readingsApi, 'fetchReadings').mockResolvedValue([
      reading({ id: 'id-mine', amount: 72.4, editable: true }),
      reading({ id: 'id-theirs', amount: 81, editable: false, memberDisplayName: 'Ben' }),
    ])
    renderAt()

    expect(await screen.findByRole('button', { name: 'Edit the reading of 72.4' })).toBeVisible()
    // R165: no control at all on somebody else's series, rather than one that
    // 403s. The flag comes from the server and is never recomputed here.
    expect(
      screen.queryByRole('button', { name: 'Edit the reading of 81.0' }),
    ).not.toBeInTheDocument()
  })

  it('records a number against the bucket the form was opened on', async () => {
    const record = vi.spyOn(readingsApi, 'recordReading').mockResolvedValue(reading())
    renderAt()
    await screen.findByRole('heading', { name: 'Body Weight' })

    await userEvent.click(screen.getByRole('button', { name: 'Record' }))
    await userEvent.type(screen.getByRole('spinbutton', { name: /Amount/ }), '71.8')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(record).toHaveBeenCalledWith(
        'id-t',
        expect.objectContaining({ amount: 71.8, note: null }),
      ),
    )
    // A civil date, never an instant: nothing in this feature puts one on the
    // wire (adr/0052).
    expect(record.mock.calls[0][1].periodStart).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('does not offer settings on somebody else’s tracker', async () => {
    vi.spyOn(trackersApi, 'fetchTracker').mockResolvedValue(tracker({ owned: false }))
    renderAt()

    await screen.findByRole('heading', { name: 'Body Weight' })
    expect(screen.queryByRole('link', { name: /Settings/ })).not.toBeInTheDocument()
    // Recording is still offered: a grant means you may write (R165).
    expect(screen.getByRole('button', { name: 'Record' })).toBeVisible()
  })
})
