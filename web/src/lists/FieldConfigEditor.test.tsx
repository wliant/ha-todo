import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it, vi } from 'vitest'
import { FieldConfigEditor } from './FieldConfigEditor'
import type { ListField } from '../api/lists'
import { theme } from '../theme/theme'

function renderEditor(fields: ListField[] = []) {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <FieldConfigEditor fields={fields} onChange={onChange} />
    </ThemeProvider>,
  )
  return onChange
}

const timeCost: ListField = {
  id: 'id-time',
  kind: 'COST',
  label: 'Time',
  required: false,
  costMode: 'FIXED',
  costUnitLabel: null,
  pointsPerUnit: null,
  choices: [{ id: 'id-5', label: '5 min', points: 10 }],
}

describe('FieldConfigEditor', () => {
  it('shows description as always on and refuses to switch it off', () => {
    renderEditor()

    const description = screen.getByRole('switch', { name: 'Description' })
    expect(description).toBeChecked()
    expect(description).toBeDisabled()
    expect(screen.getByText('Always on, always required.')).toBeVisible()
  })

  it('shows the due date as always on, because every list declares it', () => {
    // R40: the dashboard reads dates across lists, so a list that could drop
    // the field would be silently missing from it (adr/0022).
    renderEditor()

    const dueAt = screen.getByRole('switch', { name: 'Due date and time' })
    expect(dueAt).toBeChecked()
    expect(dueAt).toBeDisabled()
  })

  it('lets the owner insist on a due date without being able to remove it', async () => {
    const onChange = renderEditor()

    await userEvent.click(screen.getByRole('checkbox', { name: 'Due date and time is required' }))

    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ kind: 'DUE_AT', required: true }),
    ])
  })

  it('does not offer the due date as something to switch on', () => {
    // It has a locked row of its own; offering it twice would let a member
    // believe they had turned it off.
    renderEditor()

    expect(screen.getAllByRole('switch', { name: 'Due date and time' })).toHaveLength(1)
  })

  it('adds a field by switching it on', async () => {
    const onChange = renderEditor()

    await userEvent.click(screen.getByRole('switch', { name: 'Assignee' }))

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ kind: 'ASSIGNEE' })])
  })

  it('does not ask whether a field is required until it is on', () => {
    renderEditor()

    expect(
      screen.queryByRole('checkbox', { name: 'Assignee is required' }),
    ).not.toBeInTheDocument()
  })

  it('asks whether a field is required once it is on', () => {
    renderEditor([{ ...timeCost, id: null, kind: 'ASSIGNEE', label: null, choices: [] }])

    // Present rather than visible: MUI's real input is deliberately hidden
    // behind the box it draws.
    expect(
      screen.getByRole('checkbox', { name: 'Assignee is required' }),
    ).toBeInTheDocument()
  })

  it('adds a cost that starts out usable', async () => {
    const onChange = renderEditor()

    await userEvent.click(screen.getByRole('button', { name: 'Add a cost' }))

    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ kind: 'COST', costMode: 'FIXED' }),
    ])
  })

  it('lets a list carry more than one named cost', async () => {
    const money: ListField = {
      ...timeCost,
      id: 'id-money',
      label: 'Money',
      costMode: 'NUMERIC',
      costUnitLabel: 'dollar',
      choices: [],
    }
    renderEditor([timeCost, money])

    // R22: a chore costs both time and money, and those are different
    // questions with different units.
    const names = screen.getAllByRole('textbox', { name: 'Cost name' })
    expect(names.map((input) => (input as HTMLInputElement).value)).toEqual([
      'Time',
      'Money',
    ])
    expect(screen.getByRole('textbox', { name: 'Measured in' })).toHaveValue('dollar')
  })

  it('swaps the sub-form when a cost changes from choices to a number', async () => {
    const onChange = renderEditor([timeCost])

    await userEvent.click(screen.getByRole('button', { name: 'A number' }))

    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ costMode: 'NUMERIC', choices: [] }),
    ])
  })

  it('keeps points optional on a cost', async () => {
    const numeric: ListField = {
      ...timeCost,
      costMode: 'NUMERIC',
      costUnitLabel: 'hour',
      pointsPerUnit: null,
      choices: [],
    }
    const onChange = renderEditor([numeric])

    expect(screen.queryByRole('spinbutton', { name: 'Points per unit' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('switch', { name: /Earn points/ }))

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ pointsPerUnit: 0 })])
  })

  it('removes a cost by name', async () => {
    const onChange = renderEditor([timeCost])

    await userEvent.click(screen.getByRole('button', { name: 'Remove Time' }))

    expect(onChange).toHaveBeenCalledWith([])
  })
})
