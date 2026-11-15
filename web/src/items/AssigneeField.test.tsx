import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it, vi } from 'vitest'
import { AssigneeField } from './AssigneeField'
import type { ListMember } from '../api/lists'
import { theme } from '../theme/theme'

const members: ListMember[] = [
  { id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null },
  { id: 'id-ben', displayName: 'Ben', owner: false, avatarImageId: 'id-face' },
]

function renderField(value: string | null, required = false) {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <AssigneeField
        members={members}
        required={required}
        value={value}
        onChange={onChange}
      />
    </ThemeProvider>,
  )
  return onChange
}

describe('AssigneeField', () => {
  it('assigns in one tap, with no popup to open first', async () => {
    // 0017 R150: the dropdown cost two taps and covered the form, to choose
    // between names that fit on one line.
    const onChange = renderField(null)

    await userEvent.click(screen.getByRole('button', { name: 'Ben' }))

    expect(onChange).toHaveBeenCalledWith('id-ben')
  })

  it('says which chip is selected, not only in colour', () => {
    // A row of toggles whose state lives in a fill colour is unreadable to
    // anything that is not looking at it.
    renderField('id-ana')

    expect(screen.getByRole('button', { name: 'Ana' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Ben' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'Nobody' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('makes unassigned a visible selection rather than the absence of one', async () => {
    const onChange = renderField('id-ana')

    expect(screen.getByRole('button', { name: 'Nobody' })).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Nobody' }))

    expect(onChange).toHaveBeenCalledWith(null)
  })

  it('offers nobody when the list insists on an assignee', () => {
    renderField('id-ana', true)

    expect(screen.queryByRole('button', { name: 'Nobody' })).not.toBeInTheDocument()
  })

  it('names the chip after the member and nothing else', () => {
    // MemberAvatar is aria-hidden for this reason: a face in the accessible
    // name makes every by-name selector in the suite read "A Ana".
    renderField(null)

    expect(screen.getByRole('button', { name: 'Ben' })).toBeVisible()
  })

  it('offers only the people who can see this list, and nobody else', () => {
    // R24, in the form. The server checks it too.
    renderField(null)

    const chips = screen.getAllByRole('button')
    // Accessible names, deliberately not textContent: a member with no picture
    // gets their initials drawn inside the chip, so Ana's text content reads
    // "AAna" while her accessible name stays "Ana". That is exactly what
    // MemberAvatar's aria-hidden is for, and what every by-name selector in
    // the suite depends on.
    expect(chips.map((chip) => chip.getAttribute('aria-pressed'))).toHaveLength(3)
    expect(chips[0]).toHaveAccessibleName('Nobody')
    expect(chips[1]).toHaveAccessibleName('Ana')
    expect(chips[2]).toHaveAccessibleName('Ben')
  })
})
