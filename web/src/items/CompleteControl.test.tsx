import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CompleteControl } from './CompleteControl'
import { theme } from '../theme/theme'

/**
 * The gesture, and the reason it is only ever an accelerator.
 *
 * The note's own button is covered through ItemsSection, which is where it is
 * wired to a mutation. What is worth isolating here is the timer: a press
 * completes, a hold does not, and a press that turns into a scroll does
 * neither.
 */
function renderControl() {
  const onComplete = vi.fn()
  const onWriteNote = vi.fn()
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <CompleteControl
        description="Buy milk"
        onComplete={onComplete}
        onWriteNote={onWriteNote}
      />
    </ThemeProvider>,
  )
  return { onComplete, onWriteNote, button: screen.getByRole('button', { name: 'Complete Buy milk' }) }
}

describe('CompleteControl', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('completes on an ordinary press', async () => {
    const { onComplete, onWriteNote, button } = renderControl()

    await userEvent.click(button)

    expect(onComplete).toHaveBeenCalledOnce()
    expect(onWriteNote).not.toHaveBeenCalled()
  })

  /*
   * fireEvent rather than userEvent for these three: they are about a timer
   * racing a pointer sequence, and userEvent's own awaits do not resolve under
   * fake timers. Firing the pointer events directly keeps them deterministic -
   * and a real browser's click after the release has to be fired explicitly,
   * which is exactly the interaction being asserted.
   */

  it('offers the note after a second, and does not complete', async () => {
    vi.useFakeTimers()
    const { onComplete, onWriteNote, button } = renderControl()

    fireEvent.pointerDown(button, { clientX: 10, clientY: 10 })
    vi.advanceTimersByTime(1100)

    expect(onWriteNote).toHaveBeenCalledOnce()

    // Releasing after the hold already fired must not complete it as well.
    fireEvent.pointerUp(button)
    fireEvent.click(button)
    expect(onComplete).not.toHaveBeenCalled()
  })

  it('completes when the press is released before the second is up', async () => {
    vi.useFakeTimers()
    const { onComplete, onWriteNote, button } = renderControl()

    fireEvent.pointerDown(button, { clientX: 10, clientY: 10 })
    vi.advanceTimersByTime(400)
    fireEvent.pointerUp(button)
    fireEvent.click(button)

    expect(onComplete).toHaveBeenCalledOnce()
    expect(onWriteNote).not.toHaveBeenCalled()
  })

  it('does neither when the press turns into a scroll', async () => {
    // Without this, starting a scroll with a thumb on the button completes an
    // item nobody meant to finish.
    vi.useFakeTimers()
    const { onComplete, onWriteNote, button } = renderControl()

    fireEvent.pointerDown(button, { clientX: 10, clientY: 10 })
    fireEvent.pointerMove(button, { clientX: 10, clientY: 60 })
    vi.advanceTimersByTime(1100)

    expect(onWriteNote).not.toHaveBeenCalled()
    expect(onComplete).not.toHaveBeenCalled()
  })

  it('reaches the note by keyboard, which a hold cannot express', async () => {
    // WCAG 2.1 SC 2.5.1: holding Enter on a button fires repeated clicks, not
    // one long press, so the gesture has no keyboard analogue. This button is
    // the alternative that makes the note reachable at all (adr/0027).
    const { onComplete, onWriteNote } = renderControl()

    await userEvent.tab()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Complete Buy milk with a note' })).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    expect(onWriteNote).toHaveBeenCalledOnce()
    expect(onComplete).not.toHaveBeenCalled()
  })
})
