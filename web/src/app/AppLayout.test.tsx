import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'
import * as client from '../api/client'
import type { UserRole } from '../api/client'

function renderShell(role: UserRole) {
  vi.spyOn(client, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role,
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/lists']}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/lists" element={<p>the lists screen</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('names the signed-in member and routes around them', async () => {
    renderShell('MEMBER')

    expect(await screen.findByText('Ana')).toBeVisible()
    expect(screen.getByText('the lists screen')).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Lists' }).length).toBeGreaterThan(0)
  })

  it('offers administration to the admin only', async () => {
    renderShell('ADMIN')
    expect(await screen.findAllByRole('link', { name: 'Users' })).not.toHaveLength(0)
  })

  it('does not offer administration to a member', async () => {
    renderShell('MEMBER')
    await screen.findByText('Ana')

    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })

  it('signs out from the navigation bar', async () => {
    const logout = vi.spyOn(client, 'logout').mockResolvedValue()
    renderShell('MEMBER')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(logout).toHaveBeenCalled()
  })
})
