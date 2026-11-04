import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CreateListPage } from './CreateListPage'
import * as authApi from '../api/auth'
import * as listsApi from '../api/lists'
import { theme } from '../theme/theme'

function renderPage() {
  vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role: 'MEMBER',
  })
  vi.spyOn(listsApi, 'fetchMembers').mockResolvedValue([
    { id: 'id-ana', displayName: 'Ana' },
    { id: 'id-ben', displayName: 'Ben' },
  ])
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={['/lists/new']}>
          <Routes>
            <Route path="/lists/new" element={<CreateListPage />} />
            <Route path="/lists/:listId" element={<p>the list</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

const created = {
  id: 'id-new',
  name: 'Garden',
  description: null,
  icon: 'GARDEN' as const,
  owned: true,
  pinned: false,
  version: 0,
  members: [],
  createdAt: '2026-11-05T00:00:00Z',
}

describe('CreateListPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('creates a list shared with nobody by default', async () => {
    // A private list is the safe default, and the "my own workout list" case
    // the requirement opens with.
    const create = vi.spyOn(listsApi, 'createList').mockResolvedValue(created)
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Garden')
    await userEvent.click(screen.getByRole('button', { name: 'Create list' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith({
        name: 'Garden',
        description: undefined,
        icon: 'CHECKLIST',
        memberIds: [],
      }),
    )
  })

  it('picks an icon by its name, so sight and screen reader agree', async () => {
    const create = vi.spyOn(listsApi, 'createList').mockResolvedValue(created)
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Garden')
    await userEvent.click(screen.getByRole('button', { name: 'Garden' }))
    await userEvent.click(screen.getByRole('button', { name: 'Create list' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(expect.objectContaining({ icon: 'GARDEN' })),
    )
  })

  it('shares with the people chosen, and never lists the owner as a choice', async () => {
    const create = vi.spyOn(listsApi, 'createList').mockResolvedValue(created)
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Shared')

    expect(await screen.findByRole('checkbox', { name: 'Ben' })).not.toBeChecked()
    // The owner always holds access, so offering them would imply they could
    // be removed.
    expect(screen.queryByRole('checkbox', { name: 'Ana' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('checkbox', { name: 'Ben' }))
    await userEvent.click(screen.getByRole('button', { name: 'Create list' }))

    await waitFor(() =>
      expect(create).toHaveBeenCalledWith(expect.objectContaining({ memberIds: ['id-ben'] })),
    )
  })

  it('will not submit without a name', async () => {
    renderPage()
    expect(await screen.findByRole('button', { name: 'Create list' })).toBeDisabled()
  })

  it('surfaces the server wording when the name is already used', async () => {
    vi.spyOn(listsApi, 'createList').mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { error: 'You already have a list with that name.' } },
    })
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), 'Groceries')
    await userEvent.click(screen.getByRole('button', { name: 'Create list' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You already have a list with that name.',
    )
  })
})
