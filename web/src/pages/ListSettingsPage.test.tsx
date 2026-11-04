import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ListSettingsPage } from './ListSettingsPage'
import * as authApi from '../api/auth'
import * as listsApi from '../api/lists'
import type { ListDetail } from '../api/lists'
import { theme } from '../theme/theme'

const groceries: ListDetail = {
  id: 'id-g',
  name: 'Groceries',
  description: 'What we need',
  icon: 'GROCERIES',
  owned: true,
  pinned: false,
  version: 3,
  members: [{ id: 'id-ana', displayName: 'Ana', owner: true }],
  createdAt: '2026-11-05T00:00:00Z',
}

function renderPage(list: ListDetail | Error = groceries) {
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
  const fetchList = vi.spyOn(listsApi, 'fetchList')
  if (list instanceof Error) {
    fetchList.mockRejectedValue(list)
  } else {
    fetchList.mockResolvedValue(list)
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={['/lists/id-g/settings']}>
          <Routes>
            <Route path="/lists/:listId/settings" element={<ListSettingsPage />} />
            <Route path="/lists/:listId" element={<p>the list</p>} />
            <Route path="/lists" element={<p>all lists</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

const conflict = {
  isAxiosError: true,
  response: {
    status: 409,
    data: { error: 'Someone else changed this list.', code: 'STALE_LIST' },
  },
}

describe('ListSettingsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('starts from what the list currently says', async () => {
    renderPage()

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Groceries')
    expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('What we need')
  })

  it('sends the version it drew the form from, so a stale save is refused', async () => {
    const update = vi.spyOn(listsApi, 'updateList').mockResolvedValue(groceries)
    vi.spyOn(listsApi, 'updateListAccess').mockResolvedValue([])
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), '!')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(
        'id-g',
        expect.objectContaining({ version: 3, name: 'Groceries!' }),
      ),
    )
  })

  it('tells the member their view went stale rather than discarding the other change', async () => {
    vi.spyOn(listsApi, 'updateList').mockRejectedValue(conflict)
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), '!')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText(/Someone else changed this list/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeVisible()
  })

  it('refuses the whole screen to someone who is not the owner', async () => {
    renderPage({ ...groceries, owned: false })

    expect(
      await screen.findByText(/Only the person who created this list can change it/),
    ).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument()
  })

  it('says the list is gone when it was never shared with them', async () => {
    // 404 is also what an ungranted list answers - the two are deliberately
    // indistinguishable, so the screen says both at once (adr/0010).
    renderPage(Object.assign(new Error('not found'), {
      isAxiosError: true,
      response: { status: 404, data: {} },
    }))

    expect(await screen.findByText(/no longer exists, or was never shared/)).toBeVisible()
  })

  it('asks before deleting, because there is no undo', async () => {
    const remove = vi.spyOn(listsApi, 'deleteList').mockResolvedValue()
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Delete Groceries' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(remove).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(remove).toHaveBeenCalledWith('id-g'))
  })
})
