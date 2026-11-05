import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminPage } from './AdminPage'
import * as adminApi from '../api/admin'
import * as authApi from '../api/auth'
import * as http from '../api/http'
import type { AdminUser } from '../api/admin'

const adminAccount: AdminUser = {
  id: 'id-admin',
  username: 'admin',
  displayName: 'Admin',
  role: 'ADMIN',
  passwordRequired: true,
  enabled: true,
  createdAt: '2026-11-04T00:00:00Z',
}

const ben: AdminUser = {
  id: 'id-ben',
  username: 'ben',
  displayName: 'Ben',
  role: 'MEMBER',
  passwordRequired: false,
  enabled: true,
  createdAt: '2026-11-04T00:00:00Z',
}

function renderAdminPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-admin',
      username: 'admin',
      displayName: 'Admin',
      role: 'ADMIN',
    avatarImageId: null,
    })
    vi.spyOn(adminApi, 'fetchAdminUsers').mockResolvedValue([adminAccount, ben])
  })

  it('opens on the household with nothing selected, and no form in the way', async () => {
    renderAdminPage()

    expect(await screen.findByRole('button', { name: /Ben/ })).toBeVisible()
    expect(screen.getByText(/Pick someone to see their details/)).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument()
  })

  it('shows the selected person in the detail pane', async () => {
    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ben/ }))

    expect(screen.getByRole('heading', { name: 'Ben' })).toBeVisible()
    expect(screen.getByText('Picked from the list, no password')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Remove Ben' })).toBeVisible()
  })

  it('adds a user with no password when a password is not required', async () => {
    const create = vi.spyOn(adminApi, 'createUser').mockResolvedValue({
      ...ben,
      id: 'id-cara',
      username: 'cara',
      displayName: 'Cara',
    })

    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add someone' }))

    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Cara')
    await userEvent.type(screen.getByRole('textbox', { name: 'Login name' }), 'cara')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(create).toHaveBeenCalledWith({
      username: 'cara',
      displayName: 'Cara',
      password: undefined,
    })
  })

  it('only asks for a password once the admin says one is required', async () => {
    const create = vi.spyOn(adminApi, 'createUser').mockResolvedValue({
      ...ben,
      id: 'id-dan',
      username: 'dan',
      displayName: 'Dan',
      passwordRequired: true,
    })

    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add someone' }))
    expect(screen.queryByLabelText(/^Password/)).not.toBeInTheDocument()

    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Dan')
    await userEvent.type(screen.getByRole('textbox', { name: 'Login name' }), 'dan')
    await userEvent.click(screen.getByLabelText('Require a password'))
    await userEvent.type(screen.getByLabelText(/^Password/), 'dans-password')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(create).toHaveBeenCalledWith({
      username: 'dan',
      displayName: 'Dan',
      password: 'dans-password',
    })
  })

  it('surfaces the server wording when the login name is taken', async () => {
    vi.spyOn(adminApi, 'createUser').mockRejectedValue(new Error('409'))
    vi.spyOn(http, 'errorMessage').mockReturnValue('That login name is already taken.')

    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add someone' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'Ben Again')
    await userEvent.type(screen.getByRole('textbox', { name: 'Login name' }), 'ben')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That login name is already taken.',
    )
  })

  it('removes a member and closes the detail on them', async () => {
    const remove = vi.spyOn(adminApi, 'deleteUser').mockResolvedValue()
    const list = vi.spyOn(adminApi, 'fetchAdminUsers')

    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ben/ }))
    list.mockResolvedValue([adminAccount])
    await userEvent.click(screen.getByRole('button', { name: 'Remove Ben' }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith('id-ben'))
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Ben' })).not.toBeInTheDocument(),
    )
  })

  it('offers no way to remove the admin account', async () => {
    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: /Admin/ }))

    expect(screen.getByText(/cannot be removed/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove Admin' })).not.toBeInTheDocument()
  })
})
