import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DueAtField } from './DueAtField'
import * as authApi from '../api/auth'
import { theme } from '../theme/theme'

/**
 * The household's zone is deliberately **not** the runner's. Every expectation
 * below is an instant, and a control that quietly used the device's clock would
 * agree with a test written in the device's zone - which is the one bug R96
 * exists to prevent.
 */
const HOUSEHOLD = 'Asia/Singapore'

function renderField(value: string | null, required = false) {
  const onChange = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ThemeProvider theme={theme} defaultMode="light">
        <DueAtField value={value} required={required} onChange={onChange} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
  return onChange
}

describe('DueAtField', () => {
  beforeEach(() => {
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      timezone: HOUSEHOLD,
      avatarImageId: null,
    })
    // A Tuesday, 14:30 UTC - which is already Tuesday evening in Singapore, so
    // the two zones agree on the date and the test is about the time only.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-11-10T14:30:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('sets today at the end of the day in one tap', async () => {
    const onChange = renderField(null)

    await userEvent.click(screen.getByRole('button', { name: 'Today' }))

    // 23:59 on the 10th in Singapore (UTC+8) is 15:59 UTC the same day. R95
    // and R96 at once: the wrong zone would put this on the 11th, and midnight
    // would put it on the 10th's start rather than its end.
    expect(onChange).toHaveBeenCalledWith('2026-11-10T15:59:00.000Z')
  })

  it('sets tomorrow in one tap', async () => {
    const onChange = renderField(null)

    await userEvent.click(screen.getByRole('button', { name: 'Tomorrow' }))

    expect(onChange).toHaveBeenCalledWith('2026-11-11T15:59:00.000Z')
  })

  it('steps the date a day at a time, keeping the time it was given', async () => {
    const onChange = renderField('2026-11-10T01:00:00.000Z')

    await userEvent.click(screen.getByRole('button', { name: 'A day later' }))

    // 01:00 UTC is 09:00 Singapore on the 10th, so a day later is 09:00 on the
    // 11th - 01:00 UTC again. An hours-based step would be right here and
    // wrong across a daylight-saving boundary.
    expect(onChange).toHaveBeenCalledWith('2026-11-11T01:00:00.000Z')
  })

  it('steps backwards too', async () => {
    const onChange = renderField('2026-11-10T01:00:00.000Z')

    await userEvent.click(screen.getByRole('button', { name: 'A day earlier' }))

    expect(onChange).toHaveBeenCalledWith('2026-11-09T01:00:00.000Z')
  })

  it('will not step a date that is not set, rather than inventing one', () => {
    renderField(null)

    expect(screen.getByRole('button', { name: 'A day later' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'A day earlier' })).toBeDisabled()
  })

  it('shows the household day the value falls on, not the device day', () => {
    // 20:00 UTC on the 10th is already 04:00 on the 11th in Singapore.
    renderField('2026-11-10T20:00:00.000Z')

    expect(screen.getByLabelText(/^Date/)).toHaveValue('2026-11-11')
    expect(screen.getByLabelText(/^Time/)).toHaveValue('04:00')
  })

  it('keeps a time the member typed when the date is chosen afterwards', async () => {
    const onChange = renderField(null)

    // fireEvent, not userEvent.type: a native time input is segmented, and
    // typing into it character by character in jsdom produces nothing.
    fireEvent.change(screen.getByLabelText(/^Time/), { target: { value: '07:30' } })
    // Nothing is emitted yet: a time with no date is not an instant.
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Today' }))

    // 07:30 Singapore on the 10th is 23:30 UTC on the 9th.
    expect(onChange).toHaveBeenCalledWith('2026-11-09T23:30:00.000Z')
  })

  it('clears a date that is not required', async () => {
    const onChange = renderField('2026-11-10T01:00:00.000Z')

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))

    expect(onChange).toHaveBeenCalledWith(null)
  })

  it('offers no way to clear a date the list insists on', () => {
    renderField('2026-11-10T01:00:00.000Z', true)

    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
  })
})
