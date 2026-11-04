import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import * as client from '../api/client'
import type { LoginUser } from '../api/client'

const ana: LoginUser = {
  id: 'id-ana',
  username: 'ana',
  displayName: 'Ana',
  role: 'MEMBER',
  passwordRequired: true,
}

const ben: LoginUser = {
  id: 'id-ben',
  username: 'ben',
  displayName: 'Ben',
  role: 'MEMBER',
  passwordRequired: false,
}

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(client, 'fetchCurrentUser').mockResolvedValue(null)
    vi.spyOn(client, 'fetchLoginUsers').mockResolvedValue([ana, ben])
  })

  it('offers the household as a list to pick from instead of a login form', async () => {
    renderLoginPage()

    expect(await screen.findByRole('button', { name: /Ana/ })).toBeVisible()
    expect(screen.getByRole('button', { name: /Ben/ })).toBeVisible()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
  })

  it('says who needs a password and who does not', async () => {
    renderLoginPage()

    expect(await screen.findByRole('button', { name: /Ana/ })).toHaveTextContent('Needs a password')
    expect(screen.getByRole('button', { name: /Ben/ })).toHaveTextContent('Tap to sign in')
  })

  it('signs a passwordless member in on one click, asking for nothing', async () => {
    const login = vi.spyOn(client, 'login').mockResolvedValue({
      id: 'id-ben',
      username: 'ben',
      displayName: 'Ben',
      role: 'MEMBER',
    })

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ben/ }))

    expect(login).toHaveBeenCalledWith('ben', undefined)
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
  })

  it('asks only the chosen member for their password', async () => {
    const login = vi.spyOn(client, 'login').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
    })

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ana/ }))

    expect(screen.getByText('Signing in as')).toBeVisible()
    expect(login).not.toHaveBeenCalled()

    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(login).toHaveBeenCalledWith('ana', 'password123')
  })

  it('lets a member who picked the wrong name go back to the list', async () => {
    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ana/ }))

    await userEvent.click(screen.getByRole('button', { name: 'Not Ana?' }))

    expect(screen.getByRole('button', { name: /Ben/ })).toBeVisible()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
  })

  it('reports a failed sign-in without revealing which part was wrong', async () => {
    vi.spyOn(client, 'login').mockRejectedValue(new Error('401'))

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: /Ana/ }))
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That password did not match.')
  })

  it('shows the admin in the list like anybody else', async () => {
    vi.spyOn(client, 'fetchLoginUsers').mockResolvedValue([
      { id: 'id-admin', username: 'admin', displayName: 'Admin', role: 'ADMIN', passwordRequired: true },
      ben,
    ])

    renderLoginPage()

    expect(await screen.findByRole('button', { name: /Admin/ })).toHaveTextContent(
      'Admin · needs a password',
    )
  })

  it('says so when the household is empty rather than showing nothing', async () => {
    vi.spyOn(client, 'fetchLoginUsers').mockResolvedValue([])

    renderLoginPage()

    expect(await screen.findByText('Nobody has been added yet.')).toBeVisible()
  })
})
