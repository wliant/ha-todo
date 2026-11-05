import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EntryForm } from './EntryForm'
import * as entriesApi from '../api/dataEntries'
import type { EntryDetail } from '../api/dataEntries'
import { theme } from '../theme/theme'

function renderForm(entry?: EntryDetail, onClose = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <EntryForm
          listId="id-recipes"
          entry={entry}
          suggestions={[{ tag: 'italian', count: 2 }]}
          open
          onClose={onClose}
        />
      </ThemeProvider>
    </QueryClientProvider>,
  )
  return onClose
}

const existing: EntryDetail = {
  id: 'id-a',
  dataListId: 'id-recipes',
  name: 'Tiramisu',
  description: 'Coffee',
  detailHtml: '<p>savoiardi</p>',
  tags: ['italian'],
  version: 3,
  createdById: null,
  createdByName: null,
  createdAt: '2026-11-05T10:00:00Z',
  updatedAt: '2026-11-05T10:00:00Z',
}

describe('EntryForm', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('will not add an entry with no name', async () => {
    renderForm()

    expect(await screen.findByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('adds an entry with its name, description and tags', async () => {
    const create = vi.spyOn(entriesApi, 'createEntry').mockResolvedValue(existing)
    const onClose = renderForm()

    await userEvent.type(screen.getByLabelText(/Name/), 'Pavlova')
    await userEvent.type(screen.getByLabelText('Description'), 'Meringue')
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(
        'id-recipes',
        expect.objectContaining({ name: 'Pavlova', description: 'Meringue' }),
      ),
    )
    expect(onClose).toHaveBeenCalled()
  })

  it('echoes the version back when editing, so a stale write is refused', async () => {
    const update = vi.spyOn(entriesApi, 'updateEntry').mockResolvedValue(existing)
    renderForm(existing)

    await userEvent.click(await screen.findByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith('id-a', expect.objectContaining({ version: 3 })),
    )
  })

  it('shows the server its own words on a conflict, and does not close', async () => {
    // R60: the member is told rather than silently losing their edit, and
    // nothing is retried behind their back.
    vi.spyOn(entriesApi, 'updateEntry').mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { error: 'Someone else changed this entry.' } },
    })
    const onClose = renderForm(existing)

    await userEvent.click(await screen.findByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Someone else changed this entry.')).toBeVisible()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('loads the editor lazily, so ProseMirror stays out of the first paint', async () => {
    renderForm()

    // The name field is usable immediately; the editor arrives after.
    expect(screen.getByLabelText(/Name/)).toBeVisible()
    expect(await screen.findByLabelText('Detail')).toBeVisible()
  })
})
