import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import * as client from '../api/auth'
import type { LoginUser } from '../api/auth'

// avatarImageId null: a member is not obliged to have a picture, and the
// roster has to draw them either way.
const ana: LoginUser = {
  id: 'id-ana',
  username: 'ana',
  displayName: 'Ana',
  avatarImageId: null,
}
const ben: LoginUser = {
  id: 'id-ben',
  username: 'ben',
  displayName: 'Ben',
  avatarImageId: null,
}

/** What the API returns for "that sign-in did not work", whatever the reason. */
function unauthorized(): AxiosError {
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 401,
    statusText: 'Unauthorized',
    data: {},
    headers: {},
    config: { headers: {} },
  } as unknown as AxiosResponse)
}

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
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

    expect(await screen.findByRole('button', { name: 'Ana' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Ben' })).toBeVisible()
    expect(screen.queryByLabelText(/^Password/)).not.toBeInTheDocument()
  })

  it('says nothing about who is the admin or who needs a password (R17)', async () => {
    renderLoginPage()
    await screen.findByRole('button', { name: 'Ana' })

    // The roster is public, so the screen drawn from it must not describe
    // anyone - only name them.
    expect(screen.queryByText(/password/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/admin/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/tap to sign in/i)).not.toBeInTheDocument()
  })

  it('shows a roster row whose whole text is the name, nothing decorating it', async () => {
    // Not just the accessible name - the visible text too. An initial here
    // reads as "A Ana", which is a row describing someone rather than naming
    // them, and the e2e suite asserts on this text as well. A member with a
    // picture gets one; a member without gets a wordless placeholder.
    renderLoginPage()

    expect(await screen.findByRole('button', { name: 'Ana' })).toHaveTextContent(/^Ana$/)
  })

  it('draws the face of a member who has uploaded one (R32, adr/0019)', async () => {
    vi.spyOn(client, 'fetchLoginUsers').mockResolvedValue([
      { ...ana, avatarImageId: 'id-face' },
    ])
    renderLoginPage()

    const row = await screen.findByRole('button', { name: 'Ana' })
    expect(row.querySelector('img')).toHaveAttribute('src', '/api/images/id-face')
    // Still only the name as text.
    expect(row).toHaveTextContent(/^Ana$/)
  })

  it('signs a passwordless member in on one tap, asking for nothing', async () => {
    const login = vi.spyOn(client, 'login').mockResolvedValue({
      id: 'id-ben',
      username: 'ben',
      displayName: 'Ben',
      role: 'MEMBER',
      timezone: 'Asia/Singapore',
    avatarImageId: null,
    })

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Ben' }))

    expect(login).toHaveBeenCalledWith('ben', undefined)
    expect(screen.queryByLabelText(/^Password/)).not.toBeInTheDocument()
  })

  it('asks for a password only after the server refuses the attempt without one', async () => {
    const login = vi
      .spyOn(client, 'login')
      .mockRejectedValueOnce(unauthorized())
      .mockResolvedValueOnce({
        id: 'id-ana',
        username: 'ana',
        displayName: 'Ana',
        role: 'MEMBER',
        timezone: 'Asia/Singapore',
    avatarImageId: null,
      })

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Ana' }))

    expect(login).toHaveBeenNthCalledWith(1, 'ana', undefined)
    expect(await screen.findByLabelText(/^Password/)).toBeVisible()
    expect(screen.getByText('Signing in as')).toBeVisible()

    await userEvent.type(screen.getByLabelText(/^Password/), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(login).toHaveBeenNthCalledWith(2, 'ana', 'password123')
    // The refusal that asked the question is not reported as a failure.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports a wrong password without saying which part was wrong', async () => {
    vi.spyOn(client, 'login').mockRejectedValue(unauthorized())

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Ana' }))
    await userEvent.type(await screen.findByLabelText(/^Password/), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('did not work')
  })

  it('lets a member who picked the wrong name go back to the list', async () => {
    vi.spyOn(client, 'login').mockRejectedValue(unauthorized())

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Ana' }))
    await screen.findByLabelText(/^Password/)

    await userEvent.click(screen.getByRole('button', { name: 'Not Ana?' }))

    expect(screen.getByRole('button', { name: 'Ben' })).toBeVisible()
    expect(screen.queryByLabelText(/^Password/)).not.toBeInTheDocument()
  })

  it('distinguishes a server that is down from a password that is needed', async () => {
    vi.spyOn(client, 'login').mockRejectedValue(new Error('network down'))

    renderLoginPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Ben' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not sign in')
    expect(screen.queryByLabelText(/^Password/)).not.toBeInTheDocument()
  })

  it('says so when the household is empty rather than showing nothing', async () => {
    vi.spyOn(client, 'fetchLoginUsers').mockResolvedValue([])

    renderLoginPage()

    expect(await screen.findByText('Nobody has been added yet.')).toBeVisible()
  })
})
