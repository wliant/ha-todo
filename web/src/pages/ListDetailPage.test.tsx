import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ActivityTab, ItemsTab, ListDetailPage } from './ListDetailPage'
import * as itemsApi from '../api/items'
import * as listsApi from '../api/lists'
import * as authApi from '../api/auth'
import { theme } from '../theme/theme'

const list: listsApi.ListDetail = {
  id: 'id-list',
  name: 'Chores',
  description: null,
  icon: 'CHECKLIST',
  owned: true,
  pinned: false,
  version: 1,
  members: [{ id: 'id-ana', displayName: 'Ana', owner: true, avatarImageId: null }],
  createdAt: '2026-11-05T00:00:00Z',
}

/** The same nesting App.tsx declares, so the tabs are tested as routes. */
function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/lists/:listId" element={<ListDetailPage />}>
              <Route index element={<ItemsTab />} />
              <Route path="activity" element={<ActivityTab />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('ListDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(listsApi, 'fetchList').mockResolvedValue(list)
    vi.spyOn(listsApi, 'fetchListFields').mockResolvedValue([])
    vi.spyOn(listsApi, 'touchList').mockResolvedValue(undefined)
    vi.spyOn(itemsApi, 'fetchItems').mockResolvedValue([])
    vi.spyOn(itemsApi, 'fetchPoints').mockResolvedValue([])
    vi.spyOn(itemsApi, 'fetchActivity').mockResolvedValue({
      from: '2026-10-29T00:00:00Z',
      to: '2026-11-05T00:00:00Z',
      members: [],
      events: [],
    })
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
      avatarImageId: null,
    })
  })

  it('opens on the items, because that is what a list is for', async () => {
    renderAt('/lists/id-list')

    expect(await screen.findByRole('heading', { name: 'Items' })).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Items' })).toHaveAttribute('aria-selected', 'true')
  })

  it('reaches the activity by its own URL, not by tab state', async () => {
    // Deep-linkable, and correct under the back button, because the selected
    // tab is derived from the pathname.
    renderAt('/lists/id-list/activity')

    expect(await screen.findByRole('heading', { name: 'This week' })).toBeVisible()
    expect(screen.getByRole('tab', { name: 'Activity' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('moves between the tabs', async () => {
    renderAt('/lists/id-list')
    await screen.findByRole('heading', { name: 'Items' })

    await userEvent.click(screen.getByRole('tab', { name: 'Activity' }))

    expect(await screen.findByRole('heading', { name: 'This week' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Items' })).not.toBeInTheDocument()
  })

  it('loads the list once for both tabs', async () => {
    // The tabs are handed the list through the outlet rather than each asking
    // for it, so switching tabs is not a refetch.
    renderAt('/lists/id-list')
    await screen.findByRole('heading', { name: 'Items' })

    await userEvent.click(screen.getByRole('tab', { name: 'Activity' }))
    await screen.findByRole('heading', { name: 'This week' })

    expect(listsApi.fetchList).toHaveBeenCalledOnce()
  })
})
