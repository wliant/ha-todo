import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DetailEditor } from './DetailEditor'
import * as profileApi from '../api/profile'
import { theme } from '../theme/theme'

function renderEditor(value = '') {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme} defaultMode="light">
      <DetailEditor value={value} required={false} onChange={onChange} />
    </ThemeProvider>,
  )
  return onChange
}

function file(name: string, type: string, bytes = 10) {
  return new File([new Uint8Array(bytes)], name, { type })
}

describe('DetailEditor', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows what the item already says', async () => {
    renderEditor('<p>Heat the oven</p>')
    expect(await screen.findByText('Heat the oven')).toBeVisible()
  })

  it('offers the formatting a set of steps needs', async () => {
    renderEditor()
    for (const label of ['Bold', 'Italic', 'Bulleted list', 'Numbered steps']) {
      expect(await screen.findByRole('button', { name: label })).toBeVisible()
    }
  })

  // Whether a picture lands *beside* a selection rather than over it needs a
  // real browser to make a real selection - jsdom gives ProseMirror none, so a
  // test here would pass whatever the code did. It is covered in
  // e2e/tests/test_items.py instead.
  it('uploads a chosen picture rather than inlining it', async () => {
    // Inlining would put megabytes of base64 into a column every item read
    // returns - and the server would strip it anyway.
    const upload = vi
      .spyOn(profileApi, 'uploadDetailImage')
      .mockResolvedValue('/api/images/0f8fad5b-d9cb-469f-a165-70867728950e')
    renderEditor()

    await userEvent.upload(
      screen.getByLabelText('Add a picture'),
      file('step.png', 'image/png'),
    )

    await waitFor(() => expect(upload).toHaveBeenCalled())
    expect(upload.mock.calls[0][0].name).toBe('step.png')
  })

  it('refuses a picture that is not one of the three types', async () => {
    const upload = vi.spyOn(profileApi, 'uploadDetailImage')
    renderEditor()

    await userEvent.upload(
      screen.getByLabelText('Add a picture'),
      file('drawing.svg', 'image/svg+xml'),
    )

    expect(upload).not.toHaveBeenCalled()
  })

  it('refuses one over the limit without troubling the server', async () => {
    const upload = vi.spyOn(profileApi, 'uploadDetailImage')
    renderEditor()

    await userEvent.upload(
      screen.getByLabelText('Add a picture'),
      file('huge.png', 'image/png', 5 * 1024 * 1024 + 1),
    )

    expect(await screen.findByText(/too large/)).toBeVisible()
    expect(upload).not.toHaveBeenCalled()
  })

  it('says so when the upload fails', async () => {
    vi.spyOn(profileApi, 'uploadDetailImage').mockRejectedValue(new Error('nope'))
    renderEditor()

    await userEvent.upload(
      screen.getByLabelText('Add a picture'),
      file('step.png', 'image/png'),
    )

    expect(await screen.findByText('Could not add that picture.')).toBeVisible()
  })
})
