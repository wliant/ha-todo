import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminPage } from './AdminPage'
import * as client from '../api/client'
import type { AdminUser } from '../api/client'

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
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
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
    vi.spyOn(client, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-admin',
      username: 'admin',
      displayName: 'Admin',
      role: 'ADMIN',
    })
    vi.spyOn(client, 'fetchAdminUsers').mockResolvedValue([adminAccount, ben])
  })

  it('adds a user with no password when a password is not required', async () => {
    const create = vi.spyOn(client, 'createUser').mockResolvedValue({
      ...ben,
      id: 'id-cara',
      username: 'cara',
      displayName: 'Cara',
    })

    renderAdminPage()

    await userEvent.type(await screen.findByLabelText('Name'), 'Cara')
    await userEvent.type(screen.getByLabelText('Login name'), 'cara')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(create).toHaveBeenCalledWith({
      username: 'cara',
      displayName: 'Cara',
      password: undefined,
    })
  })

  it('only asks for a password once the admin says one is required', async () => {
    const create = vi.spyOn(client, 'createUser').mockResolvedValue({
      ...ben,
      id: 'id-dan',
      username: 'dan',
      displayName: 'Dan',
      passwordRequired: true,
    })

    renderAdminPage()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()

    await userEvent.type(await screen.findByLabelText('Name'), 'Dan')
    await userEvent.type(screen.getByLabelText('Login name'), 'dan')
    await userEvent.click(screen.getByLabelText('Require a password'))
    await userEvent.type(screen.getByLabelText('Password'), 'dans-password')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(create).toHaveBeenCalledWith({
      username: 'dan',
      displayName: 'Dan',
      password: 'dans-password',
    })
  })

  it('surfaces the server wording when the login name is taken', async () => {
    vi.spyOn(client, 'createUser').mockRejectedValue(new Error('409'))
    vi.spyOn(client, 'errorMessage').mockReturnValue('That login name is already taken.')

    renderAdminPage()
    await userEvent.type(await screen.findByLabelText('Name'), 'Ben Again')
    await userEvent.type(screen.getByLabelText('Login name'), 'ben')
    await userEvent.click(screen.getByRole('button', { name: 'Add user' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That login name is already taken.')
  })

  it('removes a member', async () => {
    const remove = vi.spyOn(client, 'deleteUser').mockResolvedValue()

    renderAdminPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Remove Ben' }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith('id-ben'))
  })

  it('offers no way to remove the admin account', async () => {
    renderAdminPage()

    expect(await screen.findByRole('button', { name: 'Remove Ben' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove Admin' })).not.toBeInTheDocument()
  })
})
