import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PendingTrackersNotice } from './PendingTrackersNotice'
import * as trackersApi from '../api/trackers'
import * as readingsApi from '../api/trackerReadings'
import type { PendingTracker } from '../api/trackers'
import { theme } from '../theme/theme'

function pending(overrides: Partial<PendingTracker> = {}): PendingTracker {
  return {
    id: 'id-w',
    name: 'Body Weight',
    icon: 'HEALTH',
    unit: 'kg',
    decimals: 1,
    periodStart: '2026-11-19',
    ...overrides,
  }
}

function renderNotice() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter>
          <PendingTrackersNotice />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

/** The agenda's one tracker-shaped line (requirements/0019 R171). */
describe('PendingTrackersNotice', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('names a daily tracker with nothing recorded today', async () => {
    vi.spyOn(trackersApi, 'fetchPendingTrackers').mockResolvedValue([pending()])
    renderNotice()

    expect(await screen.findByText('Nothing recorded today for Body Weight.')).toBeVisible()
  })

  it('shows nothing at all when everything is recorded', async () => {
    vi.spyOn(trackersApi, 'fetchPendingTrackers').mockResolvedValue([])
    renderNotice()

    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
  })

  /**
   * The one place in the app that deliberately does *not* distinguish its
   * three states. A nudge is not somebody's data, and "could not check whether
   * you weighed yourself" is noise on the landing page.
   */
  it('stays silent while loading and when the check fails', async () => {
    vi.spyOn(trackersApi, 'fetchPendingTrackers').mockRejectedValue(new Error('offline'))
    renderNotice()

    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
  })

  it('records against the bucket the server named, not one computed here', async () => {
    // R166: the server says which day it is asking about, so a device whose
    // clock is on the other side of midnight cannot file the number under
    // yesterday.
    vi.spyOn(trackersApi, 'fetchPendingTrackers').mockResolvedValue([
      pending({ periodStart: '2026-11-19' }),
    ])
    const record = vi.spyOn(readingsApi, 'recordReading').mockResolvedValue({
      id: 'id-r',
      trackerId: 'id-w',
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
    })
    renderNotice()

    await userEvent.click(await screen.findByRole('button', { name: 'Record' }))
    await userEvent.type(screen.getByRole('spinbutton', { name: /Amount/ }), '72.4')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(record).toHaveBeenCalledWith('id-w', {
        periodStart: '2026-11-19',
        amount: 72.4,
        note: null,
      }),
    )
  })

  it('offers one button per tracker when several are missing', async () => {
    vi.spyOn(trackersApi, 'fetchPendingTrackers').mockResolvedValue([
      pending({ id: 'id-w', name: 'Body Weight' }),
      pending({ id: 'id-r', name: 'Reading', unit: 'minutes' }),
    ])
    renderNotice()

    expect(await screen.findByRole('button', { name: 'Body Weight' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reading' })).toBeVisible()
    // Picking which number to record is the decision, so there is no single
    // "Record" that would have to guess.
    expect(screen.queryByRole('button', { name: 'Record' })).not.toBeInTheDocument()
  })
})
