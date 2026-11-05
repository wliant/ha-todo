import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it, vi } from 'vitest'
import { TagInput } from './TagInput'
import { theme } from '../theme/theme'

function renderInput(value: string[], onChange = vi.fn()) {
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <TagInput
        value={value}
        suggestions={[
          { tag: 'italian', count: 4 },
          { tag: 'quick', count: 2 },
        ]}
        onChange={onChange}
      />
    </ThemeProvider>,
  )
  return onChange
}

describe('TagInput', () => {
  it('offers what the collection already uses', async () => {
    renderInput([])

    await userEvent.click(screen.getByLabelText('Tags'))

    // The suggestions are the whole point: a household converges on its own
    // words rather than accumulating two spellings of each.
    expect(await screen.findByText('italian')).toBeVisible()
    expect(screen.getByText('quick')).toBeVisible()
  })

  it('normalises a typed tag so the chip matches what the server will store', async () => {
    const onChange = renderInput([])

    await userEvent.type(screen.getByLabelText('Tags'), '  Slow   Cooker  {enter}')

    expect(onChange).toHaveBeenCalledWith(['slow cooker'])
  })

  it('does not add the same tag twice', async () => {
    const onChange = renderInput(['italian'])

    await userEvent.type(screen.getByLabelText('Tags'), 'ITALIAN{enter}')

    expect(onChange).toHaveBeenCalledWith(['italian'])
  })

  it('shows the tags already on the entry', () => {
    renderInput(['desserts', 'italian'])

    expect(screen.getByText('desserts')).toBeVisible()
    expect(screen.getByText('italian')).toBeVisible()
  })
})
