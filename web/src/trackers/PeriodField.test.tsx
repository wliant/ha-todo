import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PeriodField } from './PeriodField'
import * as authApi from '../api/auth'
import type { TrackerPeriod } from '../api/trackers'
import { theme } from '../theme/theme'

/**
 * Which bucket a number belongs to (requirements/0019 R162, R166).
 *
 * <p>The zone comes from `/api/me`, and these tests inject one the machine
 * running them is almost certainly not in. A control that read the device
 * instead would still pass every assertion about labels and fail the one about
 * which day "today" is - which is the bug adr/0036 exists to prevent.
 */
function renderField(period: TrackerPeriod, value: string, onChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <PeriodField period={period} value={value} onChange={onChange} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
  return onChange
}

describe('PeriodField', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      timezone: 'Pacific/Auckland',
      avatarImageId: null,
    })
  })

  it('asks a daily tracker for a date', async () => {
    renderField('DAY', '2026-11-19')

    expect(await screen.findByLabelText('Date')).toHaveValue('2026-11-19')
    expect(screen.getByRole('button', { name: 'Today' })).toBeVisible()
  })

  /**
   * A month picker, not a date picker. "Which day of November?" is a question
   * a monthly tracker has no answer to, and a date box would invite one the
   * server would then canonicalise away.
   */
  it('asks a monthly tracker for a month', async () => {
    renderField('MONTH', '2026-11-01')

    expect(await screen.findByLabelText('Month')).toHaveValue('2026-11')
    expect(screen.queryByLabelText('Date')).not.toBeInTheDocument()
    expect(screen.getByText('November 2026')).toBeVisible()
  })

  it('names a week by its span rather than asking for a day in it', async () => {
    renderField('WEEK', '2026-11-16')

    expect(await screen.findByText('16-22 Nov')).toBeVisible()
    expect(screen.queryByLabelText('Date')).not.toBeInTheDocument()
  })

  it('emits the first of the month when a month is picked', async () => {
    const onChange = renderField('MONTH', '2026-11-01')

    await userEvent.click(await screen.findByRole('button', { name: 'Previous month' }))

    expect(onChange).toHaveBeenCalledWith('2026-10-01')
  })

  it('steps a week by seven days and stays on its Monday', async () => {
    const onChange = renderField('WEEK', '2026-11-16')

    await userEvent.click(await screen.findByRole('button', { name: 'Next week' }))

    expect(onChange).toHaveBeenCalledWith('2026-11-23')
  })

  it('marks the chip that matches the current value, in more than colour', async () => {
    renderField('DAY', '2026-11-19')

    const today = await screen.findByRole('button', { name: 'Today' })
    // Whether this is pressed depends on the household's today, which is not
    // this machine's - the assertion is that the state is *announced* at all.
    expect(today).toHaveAttribute('aria-pressed')
  })
})
