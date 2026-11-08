import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProfilePage } from './ProfilePage'
import * as authApi from '../api/auth'
import * as profileApi from '../api/profile'
import { theme } from '../theme/theme'
import type { UserRole } from '../api/auth'

function renderProfile(role: UserRole = 'MEMBER') {
  vi.spyOn(authApi, 'fetchCurrentUser').mockResolvedValue({
    id: 'id-ana',
    username: 'ana',
    displayName: 'Ana',
    role,
    timezone: 'Asia/Singapore',
    avatarImageId: null,
  })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      {/* The appearance control reads MUI's colour scheme, which needs a real
          theme context rather than the default one. */}
      <ThemeProvider theme={theme} defaultMode="system">
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
    vi.spyOn(profileApi, 'fetchPreferences').mockResolvedValue({ theme: 'SYSTEM', sidebarListCount: 10 })
  })

  it('starts on the stored preference', async () => {
    vi.spyOn(profileApi, 'updatePreferences')
    renderProfile()

    expect(await screen.findByRole('radio', { name: /Match my device/ })).toBeChecked()
  })

  it('saves how many lists the sidebar shows', async () => {
    // 0012 R102. The theme rides along because the server requires it; the
    // sidebar size does not ride along with a theme change, so neither control
    // can clobber the other.
    const update = vi
      .spyOn(profileApi, 'updatePreferences')
      .mockResolvedValue({ theme: 'SYSTEM', sidebarListCount: 4 })
    renderProfile()

    // fireEvent, not userEvent: `tab()` out of this field does not take focus
    // anywhere in jsdom, so the blur that commits the value never fires and the
    // test would pass against a control that saves nothing.
    const field = await screen.findByLabelText(/^Lists shown in each group/)
    fireEvent.change(field, { target: { value: '4' } })
    fireEvent.blur(field)

    await waitFor(() => expect(update).toHaveBeenCalledWith('SYSTEM', 4))
  })

  it('sends nothing for a size outside the bounds, rather than a request that 400s', async () => {
    const update = vi.spyOn(profileApi, 'updatePreferences')
    renderProfile()

    const field = await screen.findByLabelText(/^Lists shown in each group/)
    fireEvent.change(field, { target: { value: '99' } })
    fireEvent.blur(field)

    expect(update).not.toHaveBeenCalled()
    // And the box goes back to what is actually stored rather than keeping a
    // number the account does not hold.
    expect(field).toHaveValue(10)
  })

  it('saves a chosen colour scheme to the account, not just the browser', async () => {
    const update = vi
      .spyOn(profileApi, 'updatePreferences')
      .mockResolvedValue({ theme: 'DARK', sidebarListCount: 10 })
    renderProfile()

    await userEvent.click(await screen.findByRole('radio', { name: 'Dark' }))

    await waitFor(() => expect(update).toHaveBeenCalledWith('DARK'))
  })

  it('selects the chosen scheme at once, rather than after a round trip', async () => {
    // A radio must move under the member's finger. Held up until the server
    // answered, it reads as a control that does not work.
    let resolve = (): void => {}
    vi.spyOn(profileApi, 'updatePreferences').mockReturnValue(
      new Promise((done) => {
        resolve = () => done({ theme: 'DARK', sidebarListCount: 10 })
      }),
    )
    renderProfile()

    await userEvent.click(await screen.findByRole('radio', { name: 'Dark' }))

    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked()
    resolve()
  })

  it('puts the choice back if the server refuses it', async () => {
    vi.spyOn(profileApi, 'updatePreferences').mockRejectedValue(new Error('nope'))
    renderProfile()

    await userEvent.click(await screen.findByRole('radio', { name: 'Dark' }))

    await waitFor(() =>
      expect(screen.getByRole('radio', { name: /Match my device/ })).toBeChecked(),
    )
  })

  it('reflects a preference stored against the account', async () => {
    vi.spyOn(profileApi, 'fetchPreferences').mockResolvedValue({ theme: 'DARK', sidebarListCount: 10 })
    renderProfile()

    await waitFor(() =>
      expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked(),
    )
  })

  it('sets a password without ever asking for the current one', async () => {
    const setPassword = vi.spyOn(profileApi, 'setOwnPassword').mockResolvedValue()
    renderProfile()

    await userEvent.type(await screen.findByLabelText(/^New password/), 'hunter2')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(setPassword).toHaveBeenCalledWith('hunter2'))
    // The whole point of adr/0017: there is no current-password field at all.
    expect(screen.queryByLabelText(/current/i)).not.toBeInTheDocument()
  })

  it('turns the password requirement off by clearing it', async () => {
    const setPassword = vi.spyOn(profileApi, 'setOwnPassword').mockResolvedValue()
    renderProfile()

    await userEvent.click(
      await screen.findByRole('switch', { name: /Ask for a password/ }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    // null, not an empty string: password_hash IS NULL is what "no password
    // required" means.
    await waitFor(() => expect(setPassword).toHaveBeenCalledWith(null))
  })

  it('tells the admin their password comes from the deployment', async () => {
    renderProfile('ADMIN')

    expect(
      await screen.findByText(/set from the deployment configuration/),
    ).toBeVisible()
    expect(screen.queryByLabelText(/^New password/)).not.toBeInTheDocument()
  })
})
